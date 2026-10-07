package org.grit.daynomy.investmentcapacity.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentProductResponse(
    String id,
    String name,
    String type,
    BigDecimal baseRate,
    BigDecimal maxRate,
    long maxLimit,
    int termMonths,
    long monthlyDeposit,
    long expectedMaturityAmount,
    long expectedInterest,
    String liquidity,
    String earlyWithdrawalNote,
    boolean depositProtection,
    String eligibility,
    String recommendationReason,
    String dataNote,
    String companyName,
    String joinWay,
    List<InvestmentBenefitConditionResponse> benefitConditions,
    List<InvestmentAmountConditionResponse> amountConditions,
    String disclosureMonth,
    String sourceUrl,
    List<InvestmentProductTermResponse> termOptions) {}
