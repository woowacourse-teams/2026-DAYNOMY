package org.grit.daynomy.investmentcalendar.dto;

import java.util.List;

public record InvestmentCalendarResponse(
    int year, int month, List<InvestmentCalendarEventResponse> events) {}
