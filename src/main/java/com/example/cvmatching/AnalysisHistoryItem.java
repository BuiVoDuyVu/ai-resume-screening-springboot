package com.example.cvmatching;

import java.time.Instant;
import java.util.UUID;

public record AnalysisHistoryItem(UUID analysisId, Instant createdAt, String mode, int candidateCount,
                                  String jobDescriptionPreview) { }
