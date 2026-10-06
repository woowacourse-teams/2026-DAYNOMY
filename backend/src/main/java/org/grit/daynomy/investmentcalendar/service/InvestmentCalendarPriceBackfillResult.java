package org.grit.daynomy.investmentcalendar.service;

public record InvestmentCalendarPriceBackfillResult(
    int assetCount, int fetchedCount, int createdCount, int updatedCount, int unchangedCount) {}
