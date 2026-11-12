package com.example.cvmatching;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisDetails(UUID analysisId, Instant createdAt, String mode, String jobDescription,
                              List<String> requiredSkills, List<CandidateResult> candidates) { }
