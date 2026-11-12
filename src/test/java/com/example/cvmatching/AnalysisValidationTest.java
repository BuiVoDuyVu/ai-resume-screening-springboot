package com.example.cvmatching;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalysisValidationTest {
    private ConfigurableApplicationContext context;
    private HttpClient client;
    private JsonMapper mapper;
    private String baseUrl;

    @BeforeAll
    void startIsolatedBackend() throws IOException {
        // Reserve the offline endpoint while Spring selects its own random port,
        // then close it so AI requests receive a genuine connection refusal.
        try (ServerSocket offlineEndpoint = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            context = new SpringApplication(CvMatchingApplication.class).run(
                    "--server.port=0", "--server.address=127.0.0.1",
                    "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                    "--spring.datasource.url=jdbc:h2:mem:validation-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "--spring.datasource.username=sa", "--spring.datasource.password=",
                    "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=create-drop",
                    "--ollama.url=http://127.0.0.1:" + offlineEndpoint.getLocalPort() + "/api/embed");
        }
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
        mapper = context.getBean(JsonMapper.class);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    void stopIsolatedBackend() {
        try {
            if (client != null) client.close();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void rejectsEmptyJobDescriptionWithVietnameseMessage() throws Exception {
        assertError(analyze("", "tfidf", textFile("cv.txt", "Java Docker")),
                400, "Cần nhập mô tả tuyển dụng (JD).");
    }

    @Test
    void rejectsUnsupportedCvFormat() throws Exception {
        Upload file = new Upload("cv.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertError(analyze("Java Docker", "tfidf", file),
                400, "Chỉ nhận .txt, .pdf và .docx");
    }

    @Test
    void rejectsImageOnlyPdfWithOcrExplanation() throws Exception {
        Upload file = new Upload("cv_scan.pdf", "application/pdf", imageOnlyPdf());

        assertError(analyze("Java Docker", "tfidf", file),
                400, "Không trích xuất được chữ từ cv_scan.pdf. PDF dạng ảnh cần OCR.");
    }

    @Test
    void returnsServiceUnavailableWhenOllamaIsOffline() throws Exception {
        assertError(analyze("Java Docker", "ai", textFile("cv.txt", "Java Docker")),
                503, "Không gọi được Ollama. Hãy chạy Ollama, tải model bge-m3 hoặc chọn TF-IDF.");
    }

    @Test
    void acceptsCvWithoutRequiredSkillsAndReportsAllMissing() throws Exception {
        var response = analyze("Java Docker kinh nghiệm", "tfidf",
                textFile("cv_ban_hang.txt", "Kinh nghiệm bán hàng và chăm sóc khách hàng"));

        assertEquals(200, response.statusCode(), response.body());
        var result = mapper.readTree(response.body());
        assertEquals("tfidf", result.path("mode").asString());
        assertDoesNotThrow(() -> UUID.fromString(result.path("analysisId").asString()));
        assertEquals(2, result.path("requiredSkills").size());
        assertEquals(1, result.path("candidates").size());
        var candidate = result.path("candidates").get(0);
        assertEquals("cv_ban_hang.txt", candidate.path("fileName").asString());
        assertEquals(0, candidate.path("matchedCount").asInt());
        assertEquals(2, candidate.path("requiredCount").asInt());
        assertEquals(0, candidate.path("matchedSkills").size());
        assertEquals(result.path("requiredSkills"), candidate.path("missingSkills"));
        double similarity = candidate.path("similarity").asDouble();
        double score = candidate.path("technicalScore").asDouble();
        assertTrue(similarity > 0 && similarity <= 100, "Shared non-skill words still contribute to TF-IDF similarity");
        assertTrue(score > 0 && score <= 60, "Without skill matches, only the 60% similarity component contributes");
        assertEquals(0.6 * similarity, score, 0.1, "Allow for the API's one-decimal rounding");
    }

    @Test
    void rejectsEmptyCv() throws Exception {
        assertError(analyze("Java Docker", "tfidf", new Upload("empty.txt", "text/plain", new byte[0])),
                400, "Mỗi CV phải từ 1 byte đến tối đa 5 MB.");
    }

    @Test
    void rejectsMoreThanTenCvs() throws Exception {
        Upload[] files = new Upload[11];
        for (int i = 0; i < files.length; i++) files[i] = textFile("cv_" + i + ".txt", "Java Docker");

        assertError(analyze("Java Docker", "tfidf", files), 400, "Chọn từ 1 đến 10 CV.");
    }

    @Test
    void rejectsJobDescriptionLongerThanTenThousandCharacters() throws Exception {
        assertError(analyze("Java ".repeat(2001), "tfidf", textFile("cv.txt", "Java Docker")),
                400, "JD quá dài, tối đa 10.000 ký tự.");
    }

    private void assertError(HttpResponse<String> response, int expectedStatus, String expectedMessage) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertEquals(expectedMessage, mapper.readTree(response.body()).path("message").asString());
    }

    private HttpResponse<String> analyze(String jobDescription, String mode, Upload... files) throws Exception {
        String boundary = "cv-validation-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeTextField(body, boundary, "jobDescription", jobDescription);
        writeTextField(body, boundary, "mode", mode);
        for (Upload file : files) {
            body.writeBytes(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"files\"; filename=\"" + file.filename() + "\"\r\n"
                    + "Content-Type: " + file.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(file.content());
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void writeTextField(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Upload textFile(String filename, String content) {
        return new Upload(filename, "text/plain; charset=UTF-8", content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] imageOnlyPdf() throws IOException {
        BufferedImage image = new BufferedImage(120, 30, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawString("Java Docker", 2, 20);
        } finally {
            graphics.dispose();
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(LosslessFactory.createFromImage(document, image), 40, 700);
            }
            document.save(output);
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private record Upload(String filename, String contentType, byte[] content) { }
}
