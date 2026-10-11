package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioTransactionResponse(
    Long transactionId,
    SavedPortfolioHoldingResponse holding,
    BigDecimal totalRealizedProfitLoss) {}
