package org.grit.daynomy.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformanceResponse(
    PortfolioPerformanceStatus status,
    PortfolioPerformanceUnavailableReason reason,
    LocalDate baseDate,
    LocalDate previousBaseDate,
    List<PortfolioPerformancePointResponse> points) {}
