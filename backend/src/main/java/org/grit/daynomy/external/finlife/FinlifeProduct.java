package org.grit.daynomy.external.finlife;

import java.math.BigDecimal;

public record FinlifeProduct(
    String id,
    String companyName,
    String productName,
    String type,
    BigDecimal baseRate,
    BigDecimal maxRate,
    int termMonths,
    long maxLimit,
    String joinWay,
    String specialConditions,
    String joinMember,
    String joinDeny,
    String disclosureMonth) {}
