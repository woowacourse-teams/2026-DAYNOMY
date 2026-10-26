package org.grit.daynomy.emailsubscription.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection.HoldingPrice;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportantStockSelectionService {

  private final EmailSubscriptionRepository subscriptions;
  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final MarketTradingDayRepository tradingDays;
  private final StockDailyPriceRepository prices;

  public Optional<ImportantStockSelection> select(Long memberId, LocalDate runDate) {
    boolean eligible =
        subscriptions
            .findById(memberId)
            .filter(subscription -> subscription.getStatus() == EmailSubscriptionStatus.SUBSCRIBED)
            .filter(subscription -> !subscription.getMember().isWithdrawn())
            .isPresent();
    if (!eligible) {
      return Optional.empty();
    }

    List<PortfolioHolding> savedHoldings =
        portfolios
            .findByMemberId(memberId)
            .map(portfolio -> holdings.findAllByPortfolioIdOrderById(portfolio.getId()))
            .orElse(List.of());
    if (savedHoldings.isEmpty()) {
      return Optional.empty();
    }

    return tradingDays
        .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
            runDate)
        .flatMap(
            current ->
                tradingDays
                    .findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                        current.getBaseDate())
                    .map(previous -> select(savedHoldings, current, previous)));
  }

  private ImportantStockSelection select(
      List<PortfolioHolding> savedHoldings, MarketTradingDay current, MarketTradingDay previous) {
    List<Long> assetIds =
        savedHoldings.stream().map(holding -> holding.getAsset().getId()).toList();
    Map<Long, StockDailyPrice> currentPrices = pricesOn(assetIds, current.getBaseDate());
    Map<Long, StockDailyPrice> previousPrices = pricesOn(assetIds, previous.getBaseDate());
    List<HoldingPrice> snapshots =
        savedHoldings.stream()
            .map(
                holding -> {
                  Long assetId = holding.getAsset().getId();
                  return new HoldingPrice(
                      assetId,
                      holding.getQuantity(),
                      closePrice(currentPrices.get(assetId)),
                      closePrice(previousPrices.get(assetId)));
                })
            .toList();
    // 다른 통화의 미환산 가격을 합산해 개인 비중을 만들지 않는다.
    boolean comparableCurrency =
        savedHoldings.stream().map(holding -> holding.getAsset().getCurrency()).distinct().count()
            == 1;
    return ImportantStockSelection.select(
        current.getBaseDate(), previous.getBaseDate(), snapshots, comparableCurrency);
  }

  private Map<Long, StockDailyPrice> pricesOn(List<Long> assetIds, LocalDate date) {
    return prices.findAllByAssetIdInAndBaseDate(assetIds, date).stream()
        .collect(Collectors.toMap(price -> price.getAsset().getId(), Function.identity()));
  }

  private BigDecimal closePrice(StockDailyPrice price) {
    return price == null ? null : price.getClosePrice();
  }
}
