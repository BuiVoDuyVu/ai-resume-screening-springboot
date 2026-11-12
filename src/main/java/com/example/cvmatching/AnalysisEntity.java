package com.example.cvmatching;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class AnalysisEntity {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "job_description_id", nullable = false, unique = true)
    private JobDescriptionEntity jobDescription;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("candidateRank ASC")
    private List<MatchingResultEntity> results = new ArrayList<>();

    protected AnalysisEntity() { }

    AnalysisEntity(UUID id, Instant createdAt, String mode, JobDescriptionEntity jobDescription) {
        this.id = id;
        this.createdAt = createdAt;
        this.mode = mode;
        this.jobDescription = jobDescription;
    }

    void addResult(CandidateResult candidate, CvDocumentMetadata metadata, int rank) {
        results.add(new MatchingResultEntity(this, candidate, metadata, rank));
    }

    UUID id() {
        return id;
    }

    Instant createdAt() {
        return createdAt;
    }

    String mode() {
        return mode;
    }

    JobDescriptionEntity jobDescription() {
        return jobDescription;
    }

    List<MatchingResultEntity> results() {
        return results;
    }
}
