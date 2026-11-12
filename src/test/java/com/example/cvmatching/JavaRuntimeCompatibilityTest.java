package com.example.cvmatching;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeCompatibilityTest {
    private final CvTextService parser = new CvTextService();

    @Test
    void readsVietnameseUtf8TextAndRemovesBom() {
        String text = "Ứng viên Nguyễn Văn An: Java, Spring Boot, Docker";
        byte[] bytes = ("\uFEFF" + text).getBytes(StandardCharsets.UTF_8);

        assertEquals(text, parser.read(new InMemoryFile("ứng_viên.TXT", bytes)));
    }

    @Test
    void extractsTextFromGeneratedPdf() throws IOException {
        byte[] bytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700);
                content.showText("Java Spring Boot Docker");
                content.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }

        assertEquals("Java Spring Boot Docker", parser.read(new InMemoryFile("cv.pdf", bytes)).strip());
    }

    @Test
    void extractsVietnameseTextFromGeneratedDocx() throws IOException {
        String text = "Kỹ năng lập trình: Java, Spring Boot, PostgreSQL";
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            bytes = output.toByteArray();
        }

        assertEquals(text, parser.read(new InMemoryFile("cv.docx", bytes)).strip());
    }

    @Test
    void exportsReadableWorkbookWithVietnameseTextAndAutosizedColumns() throws IOException {
        CandidateResult candidate = new CandidateResult("ứng_viên.docx", 82.5, 75.0,
                2, 3, List.of("Java", "Docker"), List.of("Spring Boot"));
        AnalysisResponse report = new AnalysisResponse(UUID.randomUUID(), "tfidf",
                List.of("Java", "Docker", "Spring Boot"), List.of(candidate));

        byte[] bytes = new ExcelReportService().export(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("CV Matching");
            assertNotNull(sheet);
            assertEquals("Tên file CV", sheet.getRow(0).getCell(0).getStringCellValue());
            var row = sheet.getRow(1);
            assertEquals("ứng_viên.docx", row.getCell(0).getStringCellValue());
            assertEquals(82.5, row.getCell(1).getNumericCellValue());
            assertEquals(75.0, row.getCell(2).getNumericCellValue());
            assertEquals(2.0, row.getCell(3).getNumericCellValue());
            assertEquals(3.0, row.getCell(4).getNumericCellValue());
            assertEquals("Java, Docker", row.getCell(5).getStringCellValue());
            assertEquals("Spring Boot", row.getCell(6).getStringCellValue());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            for (int column = 0; column < 7; column++) {
                assertTrue(sheet.getColumnWidth(column) > 600);
                assertTrue(sheet.getColumnWidth(column) <= 18000);
            }
            assertTrue(workbook.getSheet("Ghi chu").getRow(0).getCell(0).getStringCellValue()
                    .contains("không tự động quyết định tuyển dụng"));
        }
    }

    @Test
    void startsApplicationAndServesMultipartAnalysisAndExcelExport() throws Exception {
        SpringApplication application = new SpringApplication(CvMatchingApplication.class);
        try (var context = application.run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=jdbc:h2:mem:runtime-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=create-drop");
             HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            String baseUrl = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
            String boundary = "cv-runtime-" + UUID.randomUUID();
            String text = "Kỹ năng Java, Spring Boot, Docker";
            String multipart = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"jobDescription\"\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + text + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"files\"; filename=\"cv.txt\"\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + text + "\r\n"
                    + "--" + boundary + "--\r\n";
            HttpRequest analysisRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyze"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8)).build();

            var analysis = client.send(analysisRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(200, analysis.statusCode(), analysis.body());
            var result = context.getBean(JsonMapper.class).readTree(analysis.body());
            assertEquals("tfidf", result.path("mode").asString());
            assertEquals(3, result.path("requiredSkills").size());
            assertEquals(1, result.path("candidates").size());
            var candidate = result.path("candidates").get(0);
            assertEquals("cv.txt", candidate.path("fileName").asString());
            assertEquals(100.0, candidate.path("technicalScore").asDouble());
            assertEquals(100.0, candidate.path("similarity").asDouble());
            assertEquals(3, candidate.path("matchedCount").asInt());
            assertEquals(0, candidate.path("missingSkills").size());
            UUID analysisId = UUID.fromString(result.path("analysisId").asString());
            var exportRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/export/" + analysisId))
                    .timeout(Duration.ofSeconds(30)).GET().build();

            var export = client.send(exportRequest, HttpResponse.BodyHandlers.ofByteArray());

            assertEquals(200, export.statusCode());
            assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    export.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(export.headers().firstValue("Content-Disposition").orElseThrow()
                    .contains("cv_matching_result.xlsx"));
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export.body()))) {
                var row = workbook.getSheet("CV Matching").getRow(1);
                assertEquals("cv.txt", row.getCell(0).getStringCellValue());
                assertEquals(100.0, row.getCell(1).getNumericCellValue());
                assertEquals(3.0, row.getCell(3).getNumericCellValue());
            }

            var history = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyses?limit=50"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, history.statusCode(), history.body());
            var saved = context.getBean(JsonMapper.class).readTree(history.body());
            assertEquals(1, saved.size());
            assertEquals(analysisId.toString(), saved.get(0).path("analysisId").asString());
            assertEquals(1, saved.get(0).path("candidateCount").asInt());

            var details = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyses/" + analysisId))
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, details.statusCode(), details.body());
            var restored = context.getBean(JsonMapper.class).readTree(details.body());
            assertEquals(text, restored.path("jobDescription").asString());
            assertEquals(result.path("candidates"), restored.path("candidates"));

            var invalidLimit = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyses?limit=0"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(400, invalidLimit.statusCode());

            var deleted = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/analyses/" + analysisId))
                    .DELETE().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, deleted.statusCode());
            var afterDelete = client.send(exportRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(400, afterDelete.statusCode(), afterDelete.body());
        }
    }

    private record InMemoryFile(String filename, byte[] content) implements MultipartFile {
        @Override
        public String getName() {
            return "files";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(destination.toPath(), content);
        }
    }
}
