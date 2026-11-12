package com.example.cvmatching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "matching_skills", uniqueConstraints =
        @UniqueConstraint(name = "uk_matching_skill_order", columnNames = {"matching_result_id", "skill_order"}))
public class MatchingSkillEntity {
    @Id
    @Column(name = "matching_skill_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matching_result_id", nullable = false)
    private MatchingResultEntity result;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 16)
    private SkillMatchStatus status;

    @Column(name = "skill_order", nullable = false)
    private int skillOrder;

    protected MatchingSkillEntity() { }

    MatchingSkillEntity(MatchingResultEntity result, String skillName, SkillMatchStatus status, int skillOrder) {
        this.id = UUID.randomUUID();
        this.result = result;
        this.skillName = skillName;
        this.status = status;
        this.skillOrder = skillOrder;
    }

    String skillName() {
        return skillName;
    }

    SkillMatchStatus status() {
        return status;
    }
}

enum SkillMatchStatus { MATCHED, MISSING }
