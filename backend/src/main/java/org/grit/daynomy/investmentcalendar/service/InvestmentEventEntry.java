package org.grit.daynomy.investmentcalendar.service;

import java.math.BigDecimal;
import java.time.Instant;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;

public record InvestmentEventEntry(
    InvestmentEventType type,
    String title,
    Instant announcedAt,
    BigDecimal previousValue,
    BigDecimal actualValue,
    String valueUnit,
    String sourceName,
    String sourceUrl,
    String sourceKey,
    String relatedAssetCode) {}
