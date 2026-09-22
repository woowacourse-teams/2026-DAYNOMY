package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.asset.domain.StockMarket;

public record PortfolioHoldingResponse(
    Long assetId,
    String assetCode,
    String name,
    StockMarket market,
    LocalDate baseDate,
    long quantity,
    BigDecimal averagePurchasePrice,
    BigDecimal closePrice,
    BigDecimal purchaseAmount,
    BigDecimal evaluationAmount,
    BigDecimal profitLoss,
    BigDecimal returnRate,
    BigDecimal weight) {}
