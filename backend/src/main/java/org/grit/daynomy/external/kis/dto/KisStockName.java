package org.grit.daynomy.external.kis.dto;

public record KisStockName(
    String exchangeCode, String symbol, String koreanName, String securityType) {}
