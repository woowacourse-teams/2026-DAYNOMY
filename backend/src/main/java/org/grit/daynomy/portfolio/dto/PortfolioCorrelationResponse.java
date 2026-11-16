package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record PortfolioCorrelationResponse(
    @Schema(example = "1") Long firstAssetId,
    @Schema(example = "삼성전자") String firstAssetName,
    @Schema(example = "2") Long secondAssetId,
    @Schema(example = "KODEX 200선물인버스2X") String secondAssetName,
    @Schema(description = "최근 1년 주간 원화 수익률의 피어슨 상관계수", example = "-0.72") BigDecimal correlation,
    @Schema(description = "두 자산이 함께 가진 주간 수익률 관측치 수", example = "48") int sampleCount) {}
