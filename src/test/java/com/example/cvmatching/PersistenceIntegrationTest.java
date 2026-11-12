package com.example.cvmatching;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceIntegrationTest {
    private static final List<String> TABLES = List.of("job_descriptions", "cv_documents", "analyses",
            "matching_results", "matching_skills");

    @Test
    void restoresResultsAndExcelAfterRestartWithDuplicateFilenamesAndStableOrder() throws Exception {
        String url = databaseUrl();
        String jd = "Tuyển lập trình viên Java: Spring Boot, Java, Docker";
        AnalysisResponse report = report();
        Instant createdAt;
        try (var context = open(url, "create")) {
            var persistence = context.getBean(AnalysisPersistence.class);
            persistence.save(jd, report, documents());
            createdAt = persistence.details(report.analysisId()).createdAt();
            assertNotNull(createdAt);
            assertEquals(report, persistence.get(report.analysisId()));
            var jdbc = context.getBean(JdbcTemplate.class);
            assertEquals(List.of(1L, 2L, 1L, 2L, 6L), tableCounts(jdbc));
            assertEquals("a".repeat(64), jdbc.queryForObject(
                    "SELECT sha256 FROM cv_documents WHERE size_bytes = ?", String.class, 101L));
            assertEquals("text/plain", jdbc.queryForObject(
                    "SELECT content_type FROM cv_documents WHERE size_bytes = ?", String.class, 202L));
        }

        try (var reopened = open(url, "validate")) {
            var persistence = reopened.getBean(AnalysisPersistence.class);
            AnalysisResponse restored = persistence.get(report.analysisId());
            assertEquals(report, restored);
            AnalysisDetails details = persistence.details(report.analysisId());
            assertEquals(jd, details.jobDescription());
            assertEquals(createdAt, details.createdAt());
            assertEquals(report.requiredSkills(), details.requiredSkills());
            assertEquals(report.candidates(), details.candidates());

            byte[] export = reopened.getBean(ExcelReportService.class).export(restored);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
                var sheet = workbook.getSheet("CV Matching");
                assertEquals("trùng_tên.txt", sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals("Java, Docker", sheet.getRow(1).getCell(5).getStringCellValue());
                assertEquals("Spring Boot, Java", sheet.getRow(2).getCell(5).getStringCellValue());
                assertEquals("Docker", sheet.getRow(2).getCell(6).getStringCellValue());
            }
        }
    }

    @Test
    void retainsMoreThanFiftyAnalysesAndLimitsHistoryNewestFirst() {
        try (var context = open(databaseUrl(), "create")) {
            var persistence = context.getBean(AnalysisPersistence.class);
            List<AnalysisResponse> saved = new ArrayList<>();
            for (int index = 0; index < 51; index++) {
                var report = report();
                persistence.save("Java developer " + index + "\n" + "Kỹ năng Docker ".repeat(20),
                        report, documents());
                saved.add(report);
            }

            var latest = persistence.history(2);

            assertEquals(List.of(saved.get(50).analysisId(), saved.get(49).analysisId()),
                    latest.stream().map(item -> item.analysisId()).toList());
            assertEquals(51, persistence.history(100).size());
            assertEquals(saved.get(0), persistence.get(saved.get(0).analysisId()));
            assertEquals(2, latest.get(0).candidateCount());
            assertEquals("tfidf", latest.get(0).mode());
            assertTrue(latest.get(0).jobDescriptionPreview().startsWith("Java developer 50 "));
            assertTrue(latest.get(0).jobDescriptionPreview().length() <= 160);
            assertFalse(latest.get(0).jobDescriptionPreview().contains("\n"));
            assertTrue(latest.get(0).jobDescriptionPreview().endsWith("..."));
            assertThrows(IllegalArgumentException.class, () -> persistence.history(0));
            assertThrows(IllegalArgumentException.class, () -> persistence.history(101));
        }
    }

    @Test
    void deletingAnalysisRemovesItsEntireGraphAndPreservesOtherReports() {
        try (var context = open(databaseUrl(), "create")) {
            var persistence = context.getBean(AnalysisPersistence.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var first = report();
            var second = report();
            persistence.save("Java Spring Boot Docker", first, documents());
            persistence.save("Another Java position", second, documents());
            assertEquals(List.of(2L, 4L, 2L, 4L, 12L), tableCounts(jdbc));

            persistence.delete(first.analysisId());

            assertEquals(List.of(1L, 2L, 1L, 2L, 6L), tableCounts(jdbc));
            assertEquals(second, persistence.get(second.analysisId()));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> persistence.get(first.analysisId()));
            assertEquals("Không tìm thấy kết quả. Hãy phân tích lại CV.", exception.getMessage());
            assertThrows(IllegalArgumentException.class, () -> persistence.details(first.analysisId()));

            persistence.delete(second.analysisId());

            assertEquals(List.of(0L, 0L, 0L, 0L, 0L), tableCounts(jdbc));
            assertTrue(persistence.history(100).isEmpty());
        }
    }

    @Test
    void failedMetadataInsertRollsBackAllFiveTables() {
        try (var context = open(databaseUrl(), "create")) {
            var persistence = context.getBean(AnalysisPersistence.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var invalid = List.of(new CvDocumentMetadata("trùng_tên.txt", "x".repeat(300), 101,
                            "a".repeat(64)), documents().get(1));

            assertThrows(DataIntegrityViolationException.class,
                    () -> persistence.save("Java Spring Boot Docker", report(), invalid));

            assertEquals(List.of(0L, 0L, 0L, 0L, 0L), tableCounts(jdbc));
            var valid = report();
            persistence.save("Java Spring Boot Docker", valid, documents());
            assertEquals(valid, persistence.get(valid.analysisId()));
        }
    }

    private static AnalysisResponse report() {
        var first = new CandidateResult("trùng_tên.txt", 85.0, 75.0, 2, 3,
                List.of("Java", "Docker"), List.of("Spring Boot"));
        var second = new CandidateResult("trùng_tên.txt", 85.0, 75.0, 2, 3,
                List.of("Spring Boot", "Java"), List.of("Docker"));
        return new AnalysisResponse(UUID.randomUUID(), "tfidf", List.of("Spring Boot", "Java", "Docker"),
                List.of(first, second));
    }

    private static List<CvDocumentMetadata> documents() {
        return List.of(new CvDocumentMetadata("trùng_tên.txt", "text/plain", 101, "a".repeat(64)),
                new CvDocumentMetadata("trùng_tên.txt", "text/plain", 202, "b".repeat(64)));
    }

    private static List<Long> tableCounts(JdbcTemplate jdbc) {
        return TABLES.stream().map(table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).toList();
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:history_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static ConfigurableApplicationContext open(String url, String ddlMode) {
        SpringApplication application = new SpringApplication(CvMatchingApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run("--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=" + url, "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=" + ddlMode, "--spring.jpa.open-in-view=false",
                "--spring.sql.init.mode=never");
    }
}
