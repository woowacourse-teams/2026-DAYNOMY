package org.grit.daynomy.investmentcalendar.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;

public record InvestmentCalendarEventResponse(
    Long id,
    InvestmentEventType type,
    String title,
    Instant announcedAt,
    long daysUntil,
    boolean upcoming,
    InvestmentEventValueResponse value,
    InvestmentEventSourceResponse source,
    PortfolioEventAnalysisResponse portfolioAnalysis) {

  public static InvestmentCalendarEventResponse of(
      InvestmentEvent event,
      PortfolioEventAnalysis analysis,
      LocalDate today,
      LocalDate eventDate) {
    return new InvestmentCalendarEventResponse(
        event.getId(),
        event.getType(),
        event.getTitle(),
        event.getAnnouncedAt(),
        ChronoUnit.DAYS.between(today, eventDate),
        eventDate.isAfter(today),
        InvestmentEventValueResponse.from(event),
        InvestmentEventSourceResponse.from(event),
        PortfolioEventAnalysisResponse.from(analysis));
  }
}
