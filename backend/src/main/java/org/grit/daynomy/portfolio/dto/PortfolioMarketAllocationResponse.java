package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import org.grit.daynomy.asset.domain.StockMarket;

public record PortfolioMarketAllocationResponse(
    StockMarket market, BigDecimal evaluationAmount, BigDecimal weight) {}
