package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioCalculationResponse(
    LocalDate baseDate,
    BigDecimal totalPurchaseAmount,
    BigDecimal totalEvaluationAmount,
    BigDecimal totalProfitLoss,
    BigDecimal totalReturnRate,
    List<PortfolioHoldingResponse> holdings,
    List<PortfolioMarketAllocationResponse> marketAllocations) {}
