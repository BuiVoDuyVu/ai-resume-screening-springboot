package com.example.cvmatching;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalysisPersistence {
    private final AnalysisRepository repository;

    public AnalysisPersistence(AnalysisRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(String jd, AnalysisResponse result, List<CvDocumentMetadata> documents) {
        if (documents.size() != result.candidates().size()) {
            throw new IllegalArgumentException("Thông tin CV không khớp số lượng kết quả.");
        }
        for (int rank = 0; rank < documents.size(); rank++) {
            var document = documents.get(rank);
            if (!Objects.equals(document.fileName(), result.candidates().get(rank).fileName())) {
                throw new IllegalArgumentException("Thông tin CV không khớp thứ tự kết quả.");
            }
            if (document.sizeBytes() < 0 || document.sha256() == null
                    || !document.sha256().matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Thông tin kiểm tra CV không hợp lệ.");
            }
        }
        var description = new JobDescriptionEntity(jd, result.requiredSkills());
        var analysis = new AnalysisEntity(result.analysisId(), Instant.now().truncatedTo(ChronoUnit.MICROS),
                result.mode(), description);
        for (int rank = 0; rank < result.candidates().size(); rank++) {
            analysis.addResult(result.candidates().get(rank), documents.get(rank), rank);
        }
        repository.saveAndFlush(analysis);
    }

    public AnalysisResponse get(UUID id) {
        var analysis = find(id);
        return new AnalysisResponse(analysis.id(), analysis.mode(),
                List.copyOf(analysis.jobDescription().requiredSkills()), candidates(analysis));
    }

    public AnalysisDetails details(UUID id) {
        var analysis = find(id);
        return new AnalysisDetails(analysis.id(), analysis.createdAt(), analysis.mode(),
                analysis.jobDescription().description(), List.copyOf(analysis.jobDescription().requiredSkills()),
                candidates(analysis));
    }

    public List<AnalysisHistoryItem> history(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Số kết quả lịch sử phải từ 1 đến 100.");
        }
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(analysis -> new AnalysisHistoryItem(analysis.id(), analysis.createdAt(), analysis.mode(),
                        analysis.results().size(), preview(analysis.jobDescription().description())))
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
        repository.flush();
    }

    private AnalysisEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Không tìm thấy kết quả. Hãy phân tích lại CV."));
    }

    private static List<CandidateResult> candidates(AnalysisEntity analysis) {
        return analysis.results().stream().map(item -> item.toCandidate()).toList();
    }

    private static String preview(String jd) {
        String text = jd.replaceAll("\\s+", " ").strip();
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }
}
