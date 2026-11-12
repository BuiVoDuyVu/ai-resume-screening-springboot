package com.example.cvmatching;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matching_results", uniqueConstraints =
        @UniqueConstraint(name = "uk_matching_result_rank", columnNames = {"analysis_id", "candidate_rank"}))
public class MatchingResultEntity {
    @Id
    @Column(name = "matching_result_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisEntity analysis;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "cv_document_id", nullable = false, unique = true)
    private CvDocumentEntity document;

    @Column(name = "candidate_rank", nullable = false)
    private int candidateRank;

    @Column(name = "technical_score", nullable = false)
    private double technicalScore;

    @Column(name = "similarity", nullable = false)
    private double similarity;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "required_count", nullable = false)
    private int requiredCount;

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("skillOrder ASC")
    private List<MatchingSkillEntity> skills = new ArrayList<>();

    protected MatchingResultEntity() { }

    MatchingResultEntity(AnalysisEntity analysis, CandidateResult candidate, CvDocumentMetadata metadata, int rank) {
        this.id = UUID.randomUUID();
        this.analysis = analysis;
        this.document = new CvDocumentEntity(metadata);
        this.candidateRank = rank;
        this.technicalScore = candidate.technicalScore();
        this.similarity = candidate.similarity();
        this.matchedCount = candidate.matchedCount();
        this.requiredCount = candidate.requiredCount();
        for (String skill : candidate.matchedSkills()) {
            skills.add(new MatchingSkillEntity(this, skill, SkillMatchStatus.MATCHED, skills.size()));
        }
        for (String skill : candidate.missingSkills()) {
            skills.add(new MatchingSkillEntity(this, skill, SkillMatchStatus.MISSING, skills.size()));
        }
    }

    CandidateResult toCandidate() {
        List<String> matched = skills.stream().filter(skill -> skill.status() == SkillMatchStatus.MATCHED)
                .map(skill -> skill.skillName()).toList();
        List<String> missing = skills.stream().filter(skill -> skill.status() == SkillMatchStatus.MISSING)
                .map(skill -> skill.skillName()).toList();
        return new CandidateResult(document.fileName(), technicalScore, similarity,
                matchedCount, requiredCount, matched, missing);
    }
}
