package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.dto.PortfolioCorrelationResponse;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PortfolioCorrelationService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int MINIMUM_SAMPLE_COUNT = 20;
  private static final int RESULT_LIMIT = 3;

  private final PortfolioHoldingRepository holdingRepository;
  private final StockDailyPriceRepository priceRepository;
  private final ExchangeRateService exchangeRates;

  public List<PortfolioCorrelationResponse> correlations(Long portfolioId, LocalDate requestedTo) {
    Map<Long, Asset> assets = new HashMap<>();
    holdingRepository
        .findAllByPortfolioIdOrderById(portfolioId)
        .forEach(holding -> assets.put(holding.getAsset().getId(), holding.getAsset()));
    if (assets.size() < 2) return List.of();

    LocalDate today = LocalDate.now(SEOUL);
    LocalDate to = requestedTo.isBefore(today) ? requestedTo : today.minusDays(1);
    LocalDate from = to.minusYears(1);
    List<StockDailyPrice> prices =
        priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            List.copyOf(assets.keySet()), from, to);
    Map<Long, Map<LocalDate, StockDailyPrice>> weeklyPrices = weeklyPrices(prices);
    List<LocalDate> usdDates =
        weeklyPrices.entrySet().stream()
            .filter(entry -> "USD".equals(assets.get(entry.getKey()).getCurrency()))
            .flatMap(entry -> entry.getValue().values().stream())
            .map(StockDailyPrice::getBaseDate)
            .distinct()
            .toList();
    Map<LocalDate, BigDecimal> rates = exchangeRates.ratesForDates(usdDates);
    Map<Long, Map<LocalDate, Double>> returns = new HashMap<>();
    assets.forEach(
        (assetId, asset) ->
            returns.put(
                assetId,
                weeklyReturns(weeklyPrices.getOrDefault(assetId, Map.of()), asset, rates)));

    List<Long> assetIds = assets.keySet().stream().sorted().toList();
    List<RankedCorrelation> result = new ArrayList<>();
    for (int left = 0; left < assetIds.size(); left++) {
      for (int right = left + 1; right < assetIds.size(); right++) {
        Long leftId = assetIds.get(left);
        Long rightId = assetIds.get(right);
        Correlation correlation = correlation(returns.get(leftId), returns.get(rightId));
        if (correlation == null || correlation.value() >= 0) continue;
        result.add(
            new RankedCorrelation(
                leftId,
                assets.get(leftId).getDisplayName(),
                rightId,
                assets.get(rightId).getDisplayName(),
                correlation.value(),
                correlation.sampleCount()));
      }
    }
    return result.stream()
        .sorted(
            Comparator.comparingDouble(RankedCorrelation::correlation)
                .thenComparing(RankedCorrelation::firstAssetName)
                .thenComparing(RankedCorrelation::secondAssetName))
        .limit(RESULT_LIMIT)
        .map(RankedCorrelation::toResponse)
        .toList();
  }

  private Map<Long, Map<LocalDate, StockDailyPrice>> weeklyPrices(List<StockDailyPrice> prices) {
    Map<Long, Map<LocalDate, StockDailyPrice>> weekly = new HashMap<>();
    for (StockDailyPrice price : prices) {
      LocalDate week = price.getBaseDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      weekly
          .computeIfAbsent(price.getAsset().getId(), ignored -> new TreeMap<>())
          .merge(
              week,
              price,
              (previous, next) ->
                  previous.getBaseDate().isAfter(next.getBaseDate()) ? previous : next);
    }
    return weekly;
  }

  private Map<LocalDate, Double> weeklyReturns(
      Map<LocalDate, StockDailyPrice> prices, Asset asset, Map<LocalDate, BigDecimal> rates) {
    Map<LocalDate, Double> returns = new TreeMap<>();
    LocalDate previousWeek = null;
    BigDecimal previousPrice = null;
    for (var entry : prices.entrySet()) {
      StockDailyPrice price = entry.getValue();
      BigDecimal converted = price.getClosePrice();
      if ("USD".equals(asset.getCurrency())) {
        BigDecimal rate = rates.get(price.getBaseDate());
        converted = rate == null ? null : converted.multiply(rate);
      }
      if (converted != null
          && previousPrice != null
          && entry.getKey().equals(previousWeek.plusWeeks(1))) {
        returns.put(
            entry.getKey(),
            converted.divide(previousPrice, 12, RoundingMode.HALF_UP).doubleValue() - 1);
      }
      previousWeek = entry.getKey();
      previousPrice = converted;
    }
    return returns;
  }

  private Correlation correlation(Map<LocalDate, Double> left, Map<LocalDate, Double> right) {
    List<LocalDate> commonWeeks = left.keySet().stream().filter(right::containsKey).toList();
    if (commonWeeks.size() < MINIMUM_SAMPLE_COUNT) return null;
    double leftMean = commonWeeks.stream().mapToDouble(left::get).average().orElseThrow();
    double rightMean = commonWeeks.stream().mapToDouble(right::get).average().orElseThrow();
    double covariance = 0;
    double leftVariance = 0;
    double rightVariance = 0;
    for (LocalDate week : commonWeeks) {
      double leftDeviation = left.get(week) - leftMean;
      double rightDeviation = right.get(week) - rightMean;
      covariance += leftDeviation * rightDeviation;
      leftVariance += leftDeviation * leftDeviation;
      rightVariance += rightDeviation * rightDeviation;
    }
    if (leftVariance == 0 || rightVariance == 0) return null;
    double value = covariance / Math.sqrt(leftVariance * rightVariance);
    return new Correlation(Math.max(-1, Math.min(1, value)), commonWeeks.size());
  }

  private record Correlation(double value, int sampleCount) {}

  private record RankedCorrelation(
      Long firstAssetId,
      String firstAssetName,
      Long secondAssetId,
      String secondAssetName,
      double correlation,
      int sampleCount) {

    private PortfolioCorrelationResponse toResponse() {
      return new PortfolioCorrelationResponse(
          firstAssetId,
          firstAssetName,
          secondAssetId,
          secondAssetName,
          BigDecimal.valueOf(correlation).setScale(2, RoundingMode.HALF_UP),
          sampleCount);
    }
  }
}
