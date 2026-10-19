package org.grit.daynomy.investmentcalendar.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetEventReaction(
    Long assetId, LocalDate baseDate, LocalDate reactionDate, BigDecimal returnRate) {}
