package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.asset.domain.StockMarket;

public record PortfolioMarketAllocationResponse(
    @Schema(description = "상장 시장", example = "KOSPI") StockMarket market,
    @Schema(description = "평가금액(KRW)", example = "750000.00") BigDecimal evaluationAmount,
    @Schema(description = "가격을 확인한 자산의 전체 평가금액 대비 비중(%)", example = "100.00") BigDecimal weight) {}
