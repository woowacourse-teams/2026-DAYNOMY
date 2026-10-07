package org.grit.daynomy.investmentcapacity.dto;

public record InvestmentAmountConditionResponse(
    String description, Long thresholdAmount, String status) {}
