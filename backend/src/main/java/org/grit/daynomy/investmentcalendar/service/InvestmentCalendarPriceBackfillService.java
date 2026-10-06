package org.grit.daynomy.investmentcalendar.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient.HistoricalPrice;
import org.grit.daynomy.investmentcalendar.repository.InvestmentCalendarHoldingAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InvestmentCalendarPriceBackfillService {

  private static final int START_DATE_TOLERANCE_DAYS = 10;
  private static final EnumSet<AssetCategory> SUPPORTED_CATEGORIES =
      EnumSet.of(AssetCategory.STOCK, AssetCategory.ETF);

  private final InvestmentCalendarHoldingAssetRepository holdingAssetRepository;
  private final StockDailyPriceRepository priceRepository;
  private final InvestmentCalendarHistoricalPriceClient priceClient;

  @Value("${investment-calendar.sync.history-years:5}")
  private int historyYears;

  public InvestmentCalendarPriceBackfillResult backfill(LocalDate today) {
    LocalDate requestedFrom = today.minusYears(historyYears);
    List<Asset> assets =
        holdingAssetRepository.findDistinctListedAssetsByCategories(SUPPORTED_CATEGORIES);
    int fetched = 0;
    int created = 0;
    int updated = 0;
    int unchanged = 0;

    for (Asset asset : assets) {
      try {
        AssetBackfillResult result = backfill(asset, requestedFrom, today);
        fetched += result.fetchedCount();
        created += result.createdCount();
        updated += result.updatedCount();
        unchanged += result.unchangedCount();
      } catch (RuntimeException exception) {
        log.warn(
            "Investment calendar price backfill failed: assetCode={}, errorType={}",
            asset.getAssetCode(),
            exception.getClass().getSimpleName());
      }
    }

    return new InvestmentCalendarPriceBackfillResult(
        assets.size(), fetched, created, updated, unchanged);
  }

  private AssetBackfillResult backfill(Asset asset, LocalDate requestedFrom, LocalDate today) {
    List<StockDailyPrice> existing =
        priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            List.of(asset.getId()), requestedFrom, today);
    LocalDate fetchFrom = fetchFrom(existing, requestedFrom, today);
    if (fetchFrom == null) {
      return AssetBackfillResult.empty();
    }

    List<HistoricalPrice> fetched =
        priceClient.fetch(asset.getCategory(), asset.getAssetCode(), fetchFrom, today);
    Map<LocalDate, StockDailyPrice> existingByDate = new HashMap<>();
    for (StockDailyPrice price : existing) {
      existingByDate.put(price.getBaseDate(), price);
    }

    int created = 0;
    int updated = 0;
    int unchanged = 0;
    List<StockDailyPrice> changed = new java.util.ArrayList<>();
    for (HistoricalPrice fetchedPrice : fetched) {
      StockDailyPrice stored = existingByDate.get(fetchedPrice.baseDate());
      if (stored == null) {
        changed.add(new StockDailyPrice(asset, fetchedPrice.baseDate(), fetchedPrice.closePrice()));
        created++;
      } else if (stored.synchronize(fetchedPrice.closePrice())) {
        changed.add(stored);
        updated++;
      } else {
        unchanged++;
      }
    }
    if (!changed.isEmpty()) {
      priceRepository.saveAll(changed);
    }
    return new AssetBackfillResult(fetched.size(), created, updated, unchanged);
  }

  private LocalDate fetchFrom(
      List<StockDailyPrice> existing, LocalDate requestedFrom, LocalDate today) {
    if (existing.isEmpty()) {
      return requestedFrom;
    }
    LocalDate earliest = existing.getFirst().getBaseDate();
    if (earliest.isAfter(requestedFrom.plusDays(START_DATE_TOLERANCE_DAYS))) {
      return requestedFrom;
    }
    LocalDate latest = existing.getLast().getBaseDate();
    if (latest.isBefore(today)) {
      return latest.plusDays(1);
    }
    return null;
  }

  private record AssetBackfillResult(
      int fetchedCount, int createdCount, int updatedCount, int unchangedCount) {

    private static AssetBackfillResult empty() {
      return new AssetBackfillResult(0, 0, 0, 0);
    }
  }
}
