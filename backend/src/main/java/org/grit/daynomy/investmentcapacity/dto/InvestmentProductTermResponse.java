package org.grit.daynomy.investmentcapacity.dto;

import java.math.BigDecimal;

public record InvestmentProductTermResponse(
    String id,
    int termMonths,
    BigDecimal baseRate,
    BigDecimal maxRate,
    long monthlyDeposit,
    long expectedMaturityAmount,
    long expectedInterest) {}
