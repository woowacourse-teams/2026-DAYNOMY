package org.grit.daynomy.investmentcalendar.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.domain.AssetEventReaction;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventDirection;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysisStatus;
import org.grit.daynomy.investmentcalendar.domain.PortfolioReactionStatistics;
import org.grit.daynomy.investmentcalendar.domain.PortfolioReactionStatisticsCalculator;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentCalendarPortfolioAnalysisService {

  private final StockDailyPriceRepository priceRepository;
  private final InvestmentEventRepository eventRepository;
  private final InvestmentEventReactionService reactionService;
  private final PortfolioReactionStatisticsCalculator statisticsCalculator =
      new PortfolioReactionStatisticsCalculator();

  @Transactional(readOnly = true)
  public Map<InvestmentEvent, PortfolioEventAnalysis> analyze(
      List<InvestmentCalendarHolding> holdings, List<InvestmentEvent> selectedEvents) {
    if (selectedEvents.isEmpty()) {
      return Map.of();
    }
    List<CurrentHolding> currentHoldings = currentHoldings(holdings);
    Map<InvestmentEventType, List<InvestmentEvent>> historiesByType =
        new EnumMap<>(InvestmentEventType.class);
    List<InvestmentEvent> relatedEvents =
        selectedEvents.stream()
            .filter(
                event ->
                    currentHoldings.stream()
                        .anyMatch(holding -> relatedTo(event, holding.assetCode())))
            .toList();
    for (InvestmentEvent selected : relatedEvents) {
      historiesByType.computeIfAbsent(
          selected.getType(), type -> historicalEvents(type, relatedEvents));
    }
    List<InvestmentEvent> histories =
        historiesByType.values().stream().flatMap(List::stream).toList();
    List<Long> assetIds = currentHoldings.stream().map(CurrentHolding::assetId).toList();
    Map<InvestmentEvent, List<AssetEventReaction>> reactions =
        reactionService.calculate(histories, assetIds);
    Map<InvestmentEvent, PortfolioEventAnalysis> result = new HashMap<>();
    for (InvestmentEvent selected : selectedEvents) {
      List<CurrentHolding> related =
          currentHoldings.stream()
              .filter(holding -> relatedTo(selected, holding.assetCode()))
              .toList();
      List<InvestmentEvent> preceding =
          historiesByType.getOrDefault(selected.getType(), List.of()).stream()
              .filter(history -> history.getAnnouncedAt().isBefore(selected.getAnnouncedAt()))
              .filter(history -> sameCorporateAsset(selected, history))
              .toList();
      result.put(selected, analyzeHoldings(related, preceding, reactions));
    }
    return result;
  }

  private List<InvestmentEvent> historicalEvents(
      InvestmentEventType type, List<InvestmentEvent> selectedEvents) {
    var latest =
        selectedEvents.stream()
            .filter(event -> event.getType() == type)
            .map(InvestmentEvent::getAnnouncedAt)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    return eventRepository
        .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(type, latest)
        .stream()
        .filter(
            history ->
                selectedEvents.stream()
                    .anyMatch(
                        event -> event.getType() == type && sameCorporateAsset(event, history)))
        .toList();
  }

  private PortfolioEventAnalysis analyzeHoldings(
      List<CurrentHolding> currentHoldings,
      List<InvestmentEvent> historicalEvents,
      Map<InvestmentEvent, List<AssetEventReaction>> reactionsByEvent) {
    if (currentHoldings.isEmpty()) {
      return PortfolioEventAnalysis.noPortfolio();
    }

    BigDecimal totalEvaluation =
        currentHoldings.stream()
            .map(CurrentHolding::evaluationAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<InvestmentEventDirection, List<BigDecimal>> returnsByDirection =
        statisticsCalculator.emptyGroups();
    for (InvestmentEvent historicalEvent : historicalEvents) {
      List<AssetEventReaction> reactions =
          reactionsByEvent.getOrDefault(historicalEvent, List.of());
      portfolioReturn(currentHoldings, reactions)
          .ifPresent(
              rate ->
                  returnsByDirection
                      .computeIfAbsent(historicalEvent.direction(), ignored -> new ArrayList<>())
                      .add(rate));
    }

    List<PortfolioReactionStatistics> statistics =
        statisticsCalculator.summarize(returnsByDirection, totalEvaluation);
    PortfolioEventAnalysisStatus status =
        statistics.stream().anyMatch(PortfolioReactionStatistics::available)
            ? PortfolioEventAnalysisStatus.READY
            : PortfolioEventAnalysisStatus.INSUFFICIENT_DATA;
    LocalDate priceBaseDate =
        currentHoldings.stream()
            .map(CurrentHolding::priceBaseDate)
            .min(LocalDate::compareTo)
            .orElse(null);
    return new PortfolioEventAnalysis(
        status,
        statisticsCalculator.impactLevel(statistics),
        currentHoldings.size(),
        totalEvaluation.setScale(0, RoundingMode.HALF_UP),
        priceBaseDate,
        statistics);
  }

  private List<CurrentHolding> currentHoldings(List<InvestmentCalendarHolding> holdings) {
    if (holdings.isEmpty()) {
      return List.of();
    }
    Map<Long, StockDailyPrice> prices =
        priceRepository
            .findLatestTwoByAssetIds(
                holdings.stream().map(InvestmentCalendarHolding::assetId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    price -> price.getAsset().getId(),
                    Function.identity(),
                    (first, second) ->
                        first.getBaseDate().isAfter(second.getBaseDate()) ? first : second));
    return holdings.stream()
        .filter(holding -> prices.containsKey(holding.assetId()))
        .map(
            holding -> {
              StockDailyPrice price = prices.get(holding.assetId());
              return new CurrentHolding(
                  holding.assetId(),
                  holding.assetCode(),
                  price.getClosePrice().multiply(BigDecimal.valueOf(holding.quantity())),
                  price.getBaseDate());
            })
        .toList();
  }

  private java.util.Optional<BigDecimal> portfolioReturn(
      List<CurrentHolding> holdings, List<AssetEventReaction> reactions) {
    Map<Long, BigDecimal> rateByAsset =
        reactions.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    AssetEventReaction::assetId, AssetEventReaction::returnRate));
    List<CurrentHolding> covered =
        holdings.stream().filter(holding -> rateByAsset.containsKey(holding.assetId())).toList();
    BigDecimal coveredEvaluation =
        covered.stream()
            .map(CurrentHolding::evaluationAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (coveredEvaluation.signum() == 0) {
      return java.util.Optional.empty();
    }
    BigDecimal weightedReturn =
        covered.stream()
            .map(
                holding ->
                    rateByAsset
                        .get(holding.assetId())
                        .multiply(holding.evaluationAmount())
                        .divide(coveredEvaluation, 8, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return java.util.Optional.of(weightedReturn);
  }

  private boolean relatedTo(InvestmentEvent event, String assetCode) {
    return event.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || event.getRelatedAssetCode() == null
        || event.getRelatedAssetCode().equals(assetCode);
  }

  private boolean sameCorporateAsset(InvestmentEvent selected, InvestmentEvent historical) {
    return selected.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || java.util.Objects.equals(
            selected.getRelatedAssetCode(), historical.getRelatedAssetCode());
  }

  private record CurrentHolding(
      Long assetId, String assetCode, BigDecimal evaluationAmount, LocalDate priceBaseDate) {}
}
