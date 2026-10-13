package org.grit.daynomy.portfolio.dto;

import java.time.Instant;

public record PortfolioAnalysisHistoryResponse(
    Long analysisId,
    Long previousAnalysisId,
    Instant analyzedAt,
    String changeReason,
    PortfolioAnalysisResponse analysis) {}
