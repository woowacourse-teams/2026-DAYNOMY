package org.grit.daynomy.portfolio.v2.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.grit.daynomy.portfolio.v2.domain.PortfolioSnapshotCalculatorV2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioSnapshotServiceV2 {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int DATES_PER_BATCH = 31;

  private final StockDailyPriceRepository priceRepository;
  private final PortfolioDailySnapshotRepository snapshotRepository;
  private final PortfolioLedgerCalculator ledgerCalculator;
  private final PortfolioSnapshotCalculatorV2 calculator;
  private final org.grit.daynomy.exchangerate.service.ExchangeRateService exchangeRates;

  // 호출자는 회원 → 포트폴리오 순서로 잠금을 확보한 뒤 같은 트랜잭션에서 호출한다.
  @Transactional(propagation = Propagation.MANDATORY)
  public void refresh(
      Portfolio portfolio, List<PortfolioHoldingHistory> histories, LocalDate fromDate) {
    LocalDate today = LocalDate.now(SEOUL);
    if (!fromDate.isBefore(today)) {
      return;
    }
    List<PortfolioDailySnapshot> existing =
        snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
            portfolio.getId(), fromDate, today.minusDays(1));
    Map<LocalDate, PortfolioDailySnapshot> existingByDate = new HashMap<>();
    existing.forEach(snapshot -> existingByDate.put(snapshot.getBaseDate(), snapshot));
    NavigableSet<LocalDate> priceDates =
        new TreeSet<>(priceRepository.findDistinctBaseDatesOrderByBaseDate());
    NavigableSet<LocalDate> candidates = new TreeSet<>(priceDates);
    candidates.addAll(existingByDate.keySet());
    List<LocalDate> dates = candidates.subSet(fromDate, true, today, false).stream().toList();
    List<Long> assetIds =
        histories.stream().map(history -> history.getAsset().getId()).distinct().toList();

    for (int offset = 0; offset < dates.size(); offset += DATES_PER_BATCH) {
      List<LocalDate> batch =
          dates.subList(offset, Math.min(offset + DATES_PER_BATCH, dates.size()));
      LocalDate previous = priceDates.lower(batch.getFirst());
      LocalDate priceFrom = previous == null ? batch.getFirst() : previous;
      Map<LocalDate, Map<Long, BigDecimal>> prices = new HashMap<>();
      if (!assetIds.isEmpty()) {
        for (StockDailyPrice price :
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                assetIds, priceFrom, batch.getLast())) {
          BigDecimal converted =
              "USD".equals(price.getAsset().getCurrency())
                  ? exchangeRates.priceInKrwOrNull(price)
                  : price.getClosePrice();
          if (converted == null) continue;
          prices
              .computeIfAbsent(price.getBaseDate(), ignored -> new HashMap<>())
              .put(price.getAsset().getId(), converted);
        }
      }
      List<PortfolioDailySnapshot> additions = new ArrayList<>();
      List<PortfolioDailySnapshot> removals = new ArrayList<>();
      for (LocalDate day : batch) {
        LocalDate previousDay = priceDates.lower(day);
        var positions =
            ledgerCalculator
                .replay(
                    histories.stream()
                        .filter(history -> !history.getEffectiveDate().isAfter(day))
                        .toList())
                .positions();
        var amounts =
            calculator.calculate(
                positions.values(),
                prices.getOrDefault(day, Map.of()),
                previousDay == null ? Map.of() : prices.getOrDefault(previousDay, Map.of()),
                previousDay != null);
        PortfolioDailySnapshot snapshot = existingByDate.get(day);
        if (amounts.isEmpty()) {
          if (snapshot != null) {
            removals.add(snapshot);
          }
          continue;
        }
        boolean isNew = snapshot == null;
        if (isNew) {
          snapshot = new PortfolioDailySnapshot(portfolio, day);
        }
        var value = amounts.orElseThrow();
        snapshot.update(
            value.totalPurchase(),
            value.totalEvaluation(),
            value.totalProfitLoss(),
            value.totalReturnRate(),
            value.dailyProfitLoss(),
            value.dailyReturnRate());
        if (isNew) {
          additions.add(snapshot);
        }
      }
      // 기존 managed entity는 dirty checking으로 저장한다. 날짜마다 flush하지 않는다.
      if (!removals.isEmpty()) {
        snapshotRepository.deleteAll(removals);
      }
      if (!additions.isEmpty()) {
        snapshotRepository.saveAll(additions);
      }
    }
  }
}
