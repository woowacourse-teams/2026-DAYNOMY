package org.grit.daynomy.investmentcalendar.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarEventResponse;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentCalendarService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final InvestmentEventRepository eventRepository;
  private final InvestmentCalendarPortfolioAnalysisService portfolioAnalysisService;

  @Transactional(readOnly = true)
  public InvestmentCalendarResponse get(Long memberId, YearMonth yearMonth) {
    Instant from = yearMonth.atDay(1).atStartOfDay(SEOUL).toInstant();
    Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
    LocalDate today = LocalDate.now(SEOUL);
    List<InvestmentCalendarEventResponse> events =
        eventRepository
            .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(from, to)
            .stream()
            .map(event -> response(memberId, event, today))
            .toList();
    return new InvestmentCalendarResponse(yearMonth.getYear(), yearMonth.getMonthValue(), events);
  }

  private InvestmentCalendarEventResponse response(
      Long memberId, InvestmentEvent event, LocalDate today) {
    LocalDate eventDate = event.getAnnouncedAt().atZone(SEOUL).toLocalDate();
    return InvestmentCalendarEventResponse.of(
        event, portfolioAnalysisService.analyze(memberId, event), today, eventDate);
  }
}
