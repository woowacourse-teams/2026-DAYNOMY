package org.grit.daynomy.investmentcalendar.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentCalendarPortfolioAnalysisService {

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingRepository holdingRepository;
  private final StockDailyPriceRepository priceRepository;
  private final InvestmentEventRepository eventRepository;
  private final InvestmentEventReactionService reactionService;
  private final PortfolioReactionStatisticsCalculator statisticsCalculator =
      new PortfolioReactionStatisticsCalculator();

  @Transactional(readOnly = true)
  public PortfolioEventAnalysis analyze(Long memberId, InvestmentEvent selectedEvent) {
    List<CurrentHolding> holdings = currentHoldings(memberId, selectedEvent);
    if (holdings.isEmpty()) {
      return PortfolioEventAnalysis.noPortfolio();
    }

    BigDecimal totalEvaluation =
        holdings.stream()
            .map(CurrentHolding::evaluationAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<InvestmentEventDirection, List<BigDecimal>> returnsByDirection =
        statisticsCalculator.emptyGroups();
    List<InvestmentEvent> historicalEvents =
        eventRepository
            .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                selectedEvent.getType(), selectedEvent.getAnnouncedAt())
            .stream()
            .filter(event -> sameCorporateAsset(selectedEvent, event))
            .toList();

    List<Long> assetIds = holdings.stream().map(CurrentHolding::assetId).toList();
    for (InvestmentEvent historicalEvent : historicalEvents) {
      List<AssetEventReaction> reactions = reactionService.calculate(historicalEvent, assetIds);
      portfolioReturn(holdings, reactions)
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
        holdings.stream().map(CurrentHolding::priceBaseDate).min(LocalDate::compareTo).orElse(null);
    return new PortfolioEventAnalysis(
        status,
        statisticsCalculator.impactLevel(statistics),
        holdings.size(),
        totalEvaluation.setScale(0, RoundingMode.HALF_UP),
        priceBaseDate,
        statistics);
  }

  private List<CurrentHolding> currentHoldings(Long memberId, InvestmentEvent event) {
    return portfolioRepository
        .findByMemberId(memberId)
        .map(portfolio -> holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()))
        .orElseGet(List::of)
        .stream()
        .filter(holding -> relatedTo(event, holding))
        .map(this::withLatestPrice)
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private CurrentHolding withLatestPrice(PortfolioHolding holding) {
    StockDailyPrice price =
        priceRepository
            .findFirstByAssetIdOrderByBaseDateDesc(holding.getAsset().getId())
            .orElse(null);
    if (price == null) {
      return null;
    }
    return new CurrentHolding(
        holding.getAsset().getId(),
        price.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity())),
        price.getBaseDate());
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

  private boolean relatedTo(InvestmentEvent event, PortfolioHolding holding) {
    return event.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || event.getRelatedAssetCode() == null
        || event.getRelatedAssetCode().equals(holding.getAsset().getAssetCode());
  }

  private boolean sameCorporateAsset(InvestmentEvent selected, InvestmentEvent historical) {
    return selected.getType() != InvestmentEventType.CORPORATE_EARNINGS
        || java.util.Objects.equals(
            selected.getRelatedAssetCode(), historical.getRelatedAssetCode());
  }

  private record CurrentHolding(
      Long assetId, BigDecimal evaluationAmount, LocalDate priceBaseDate) {}
}
