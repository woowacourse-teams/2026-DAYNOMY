package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.EmailDigestSupplement;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailDigestSupplementService {
  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioHoldingHistoryRepository histories;
  private final PortfolioDailySnapshotRepository snapshots;
  private final MarketTradingDayRepository tradingDays;
  private final InvestmentEventRepository events;
  private final EmailDeliveryRepository deliveries;

  public EmailDigestSupplement compose(Long memberId, Instant now) {
    var date = now.atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    var portfolio = portfolios.findByMemberId(memberId).orElse(null);
    var positions =
        portfolio == null
            ? List.<org.grit.daynomy.portfolio.domain.PortfolioHolding>of()
            : holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    Set<String> codes =
        positions.stream()
            .map(position -> position.getAsset())
            .filter(
                asset ->
                    asset.getCategory() == AssetCategory.STOCK
                        && (asset.getMarket() == StockMarket.KOSPI
                            || asset.getMarket() == StockMarket.KOSDAQ))
            .map(asset -> asset.getAssetCode())
            .collect(Collectors.toSet());
    var yesterdayCutoff =
        date.minusDays(1).atTime(9, 0).atZone(EmailAnalysisPolicy.ZONE).toInstant();
    var candidates =
        events
            .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
                now, date.plusDays(2).atStartOfDay(EmailAnalysisPolicy.ZONE).toInstant())
            .stream()
            .filter(event -> event.getAnnouncedAt().isAfter(now) && event.getActualValue() == null)
            .filter(
                event ->
                    event.getType() != InvestmentEventType.CORPORATE_EARNINGS
                        || codes.contains(event.getRelatedAssetCode()))
            .filter(
                event ->
                    event
                            .getAnnouncedAt()
                            .atZone(EmailAnalysisPolicy.ZONE)
                            .toLocalDate()
                            .isAfter(date)
                        || (event.getType() != InvestmentEventType.CORPORATE_EARNINGS
                            && event.getUpdatedAt() != null
                            && event.getUpdatedAt().isAfter(yesterdayCutoff)))
            .map(
                event ->
                    new EmailDigestSupplement.Event(
                        event.getId() + "@" + event.getAnnouncedAt().getEpochSecond(),
                        event.getTitle(),
                        event.getAnnouncedAt(),
                        event.getSourceUrl(),
                        event.getType() != InvestmentEventType.CORPORATE_EARNINGS))
            .sorted(
                java.util.Comparator.comparing(EmailDigestSupplement.Event::announcedAt)
                    .thenComparing(EmailDigestSupplement.Event::key))
            .toList();
    var covered =
        candidates.isEmpty()
            ? List.<String>of()
            : deliveries.findCoveredEventKeys(
                memberId, candidates.stream().map(EmailDigestSupplement.Event::key).toList());
    var upcoming =
        candidates.stream().filter(event -> !covered.contains(event.key())).limit(2).toList();
    EmailDigestSupplement.Assets assets = null;
    if (!positions.isEmpty()) {
      var day =
          tradingDays
              .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                  date.minusDays(1));
      if (day.isPresent()) {
        var base = day.get().getBaseDate();
        boolean changed =
            !histories
                .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
                    portfolio.getId(), base.plusDays(1), date)
                .isEmpty();
        if (!changed && !deliveries.hasCoveredAssetSummary(memberId, base.toString())) {
          var snapshot =
              snapshots.findByPortfolioIdAndBaseDate(portfolio.getId(), base).orElse(null);
          if (snapshot != null
              && snapshot.getDailyProfitLoss() != null
              && snapshot.getDailyReturnRate() != null
              && snapshot
                      .getTotalEvaluationAmount()
                      .subtract(snapshot.getDailyProfitLoss())
                      .signum()
                  > 0)
            assets =
                new EmailDigestSupplement.Assets(
                    base,
                    snapshot.getTotalEvaluationAmount(),
                    snapshot.getDailyProfitLoss(),
                    snapshot.getDailyReturnRate());
        }
      }
    }
    return new EmailDigestSupplement(assets, upcoming);
  }
}
