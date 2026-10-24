package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PortfolioAnalysisHistoryResponse(
    @Schema(description = "분석 이력 ID", example = "1") Long analysisId,
    @Schema(description = "직전 분석 ID. 최초 분석이거나 직전 이력이 삭제되었으면 null", nullable = true)
        Long previousAnalysisId,
    @Schema(description = "분석 생성 시각(UTC)") Instant analyzedAt,
    @Schema(description = "이 분석이 생성된 변경 사유") String changeReason,
    @Schema(description = "이력에 저장된 분석 결과") PortfolioAnalysisResponse analysis) {}
