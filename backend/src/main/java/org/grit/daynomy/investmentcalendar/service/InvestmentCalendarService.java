package org.grit.daynomy.investmentcalendar.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarEventResponse;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentCalendarService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final InvestmentEventRepository eventRepository;
  private final InvestmentCalendarPortfolioAnalysisService portfolioAnalysisService;
  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingRepository holdingRepository;

  @Transactional(readOnly = true)
  public InvestmentCalendarResponse get(
      Long memberId, YearMonth yearMonth, InvestmentCalendarScope scope) {
    Instant from = yearMonth.atDay(1).atStartOfDay(SEOUL).toInstant();
    Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
    LocalDate today = LocalDate.now(SEOUL);
    Set<String> holdingAssetCodes = holdingAssetCodes(memberId, scope);
    List<InvestmentCalendarEventResponse> events =
        eventRepository
            .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(from, to)
            .stream()
            .filter(event -> visible(event, scope, holdingAssetCodes))
            .map(event -> response(memberId, event, today))
            .toList();
    return new InvestmentCalendarResponse(yearMonth.getYear(), yearMonth.getMonthValue(), events);
  }

  private Set<String> holdingAssetCodes(Long memberId, InvestmentCalendarScope scope) {
    if (scope == InvestmentCalendarScope.ALL) {
      return Set.of();
    }
    return portfolioRepository
        .findByMemberId(memberId)
        .map(portfolio -> holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()))
        .orElseGet(List::of)
        .stream()
        .map(holding -> holding.getAsset().getAssetCode())
        .collect(Collectors.toUnmodifiableSet());
  }

  private boolean visible(
      InvestmentEvent event, InvestmentCalendarScope scope, Set<String> holdingAssetCodes) {
    return scope == InvestmentCalendarScope.ALL
        || event.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || holdingAssetCodes.contains(event.getRelatedAssetCode());
  }

  private InvestmentCalendarEventResponse response(
      Long memberId, InvestmentEvent event, LocalDate today) {
    LocalDate eventDate = event.getAnnouncedAt().atZone(SEOUL).toLocalDate();
    return InvestmentCalendarEventResponse.of(
        event, portfolioAnalysisService.analyze(memberId, event), today, eventDate);
  }
}
