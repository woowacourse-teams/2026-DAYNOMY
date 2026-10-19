package org.grit.daynomy.investmentcalendar.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
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
  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public InvestmentCalendarResponse get(
      Long memberId, YearMonth yearMonth, InvestmentCalendarScope scope) {
    return get(yearMonth, scope, memberHoldings(memberId));
  }

  @Transactional(readOnly = true)
  public InvestmentCalendarResponse getForGuest(
      YearMonth yearMonth, InvestmentCalendarScope scope, Map<Long, Long> requestedHoldings) {
    return get(yearMonth, scope, resolveHoldings(requestedHoldings));
  }

  private InvestmentCalendarResponse get(
      YearMonth yearMonth,
      InvestmentCalendarScope scope,
      List<InvestmentCalendarHolding> holdings) {
    Set<String> holdingAssetCodes =
        holdings.stream()
            .map(InvestmentCalendarHolding::assetCode)
            .collect(Collectors.toUnmodifiableSet());
    Instant from = yearMonth.atDay(1).atStartOfDay(SEOUL).toInstant();
    Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
    LocalDate today = LocalDate.now(SEOUL);
    List<InvestmentEvent> visibleEvents =
        eventRepository
            .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(from, to)
            .stream()
            .filter(event -> visible(event, scope, holdingAssetCodes))
            .toList();
    Map<InvestmentEvent, PortfolioEventAnalysis> analyses =
        portfolioAnalysisService.analyze(holdings, visibleEvents);
    List<InvestmentCalendarEventResponse> events =
        visibleEvents.stream().map(event -> response(event, analyses.get(event), today)).toList();
    return new InvestmentCalendarResponse(yearMonth.getYear(), yearMonth.getMonthValue(), events);
  }

  private List<InvestmentCalendarHolding> resolveHoldings(Map<Long, Long> quantityByAssetId) {
    return assetRepository.findAllById(quantityByAssetId.keySet()).stream()
        .map(
            asset ->
                new InvestmentCalendarHolding(
                    asset.getId(), asset.getAssetCode(), quantityByAssetId.get(asset.getId())))
        .toList();
  }

  private List<InvestmentCalendarHolding> memberHoldings(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .map(portfolio -> holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()))
        .orElseGet(List::of)
        .stream()
        .map(
            holding ->
                new InvestmentCalendarHolding(
                    holding.getAsset().getId(),
                    holding.getAsset().getAssetCode(),
                    holding.getQuantity()))
        .toList();
  }

  private boolean visible(
      InvestmentEvent event, InvestmentCalendarScope scope, Set<String> holdingAssetCodes) {
    return scope == InvestmentCalendarScope.ALL
        || event.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || holdingAssetCodes.contains(event.getRelatedAssetCode());
  }

  private InvestmentCalendarEventResponse response(
      InvestmentEvent event, PortfolioEventAnalysis analysis, LocalDate today) {
    LocalDate eventDate = event.getAnnouncedAt().atZone(SEOUL).toLocalDate();
    return InvestmentCalendarEventResponse.of(event, analysis, today, eventDate);
  }
}
