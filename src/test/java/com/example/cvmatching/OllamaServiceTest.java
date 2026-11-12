package com.example.cvmatching;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class OllamaServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void sendsOrderedInputsAndConfiguredModelAndCalculatesCosineScores() throws Exception {
        try (StubOllama stub = new StubOllama(200,
                "{\"embeddings\":[[1,0],[1,0],[3,4],[0,1]]}")) {
            var service = new OllamaService(MAPPER, stub.url(), "test-embedding-model");
            String jd = "Tuyển lập trình viên Java";
            var cvs = List.of("Ứng viên Nguyễn An", "Kỹ năng Python", "Thiết kế giao diện");

            var scores = service.compare(jd, cvs);

            assertEquals(3, scores.size());
            assertEquals(1.0, scores.get(0), 1e-12);
            assertEquals(0.6, scores.get(1), 1e-12);
            assertEquals(0.0, scores.get(2), 1e-12);
            Request request = stub.request.get();
            assertNotNull(request);
            assertEquals("POST", request.method());
            assertEquals("application/json", request.contentType());
            var payload = MAPPER.readTree(request.body());
            assertEquals("test-embedding-model", payload.path("model").asString());
            assertTrue(payload.path("truncate").asBoolean());
            var inputs = payload.path("input");
            assertEquals(4, inputs.size());
            assertEquals(jd, inputs.get(0).asString());
            for (int index = 0; index < cvs.size(); index++) {
                assertEquals(cvs.get(index), inputs.get(index + 1).asString());
            }
        }
    }

    @Test
    void oppositeVectorsAndZeroVectorsHaveZeroSimilarity() throws Exception {
        try (StubOllama stub = new StubOllama(200,
                "{\"embeddings\":[[1,0],[-1,0],[0,0]]}")) {
            var service = new OllamaService(MAPPER, stub.url(), "test-model");

            assertEquals(List.of(0.0, 0.0), service.compare("Java", List.of("Opposite", "Zero")));
        }
        try (StubOllama stub = new StubOllama(200,
                "{\"embeddings\":[[0,0],[3,4]]}")) {
            var service = new OllamaService(MAPPER, stub.url(), "test-model");

            assertEquals(List.of(0.0), service.compare("Zero job description", List.of("Java")));
        }
    }

    @Test
    void nonSuccessfulHttpResponsesOfferTfidfFallback() throws Exception {
        for (int status : List.of(400, 500, 503)) {
            assertUnavailableResponse(status, "{\"embeddings\":[[1,0],[1,0]]}");
        }
    }

    @Test
    void missingOrIncorrectEmbeddingCountsAreUnavailable() throws Exception {
        for (String response : List.of("{}", "{\"embeddings\":{}}", "{\"embeddings\":[]}",
                "{\"embeddings\":[[1,0]]}", "{\"embeddings\":[[1,0],[1,0],[1,0]]}")) {
            assertUnavailableResponse(200, response);
        }
    }

    @Test
    void malformedJsonAndInvalidVectorDimensionsAreUnavailable() throws Exception {
        for (String response : List.of("not-json", "{\"embeddings\":[[1,0],[1]]}",
                "{\"embeddings\":[[],[]]}")) {
            assertUnavailableResponse(200, response);
        }
    }

    @Test
    void refusedConnectionOffersTfidfFallback() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = reserved.getLocalPort();
        }
        var service = new OllamaService(MAPPER, "http://127.0.0.1:" + port + "/api/embed", "test-model");

        var exception = assertThrows(OllamaUnavailableException.class,
                () -> service.compare("Java", List.of("Spring Boot")));

        assertTrue(exception.getMessage().contains("TF-IDF"));
    }

    private static void assertUnavailableResponse(int status, String response) throws Exception {
        try (StubOllama stub = new StubOllama(status, response)) {
            var service = new OllamaService(MAPPER, stub.url(), "test-model");

            var exception = assertThrows(OllamaUnavailableException.class,
                    () -> service.compare("Java", List.of("Spring Boot")),
                    "Expected unavailable response for HTTP " + status + ": " + response);

            assertTrue(exception.getMessage().contains("TF-IDF"));
            assertNotNull(stub.request.get(), "The request must reach the local stub");
        }
    }

    private record Request(String method, String contentType, String body) { }

    private static final class StubOllama implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<Request> request = new AtomicReference<>();

        private StubOllama(int status, String response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "ollama-service-test");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/embed", exchange -> {
                try (exchange) {
                    request.set(new Request(exchange.getRequestMethod(),
                            exchange.getRequestHeaders().getFirst("Content-Type"),
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    byte[] body = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/embed";
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
