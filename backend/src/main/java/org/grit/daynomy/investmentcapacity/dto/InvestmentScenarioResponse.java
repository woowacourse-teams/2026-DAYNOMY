package org.grit.daynomy.investmentcapacity.dto;

import org.grit.daynomy.investmentcapacity.domain.PlanStatus;

public record InvestmentScenarioResponse(
    String type,
    String label,
    String description,
    long monthlySaving,
    long monthlyInvesting,
    long monthlyEmergencyFund,
    long expectedAmount,
    PlanStatus status,
    String caution) {}
