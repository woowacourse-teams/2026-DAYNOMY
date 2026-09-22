package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockPricePersistenceService {

  private final AssetRepository assetRepository;
  private final StockDailyPriceRepository stockDailyPriceRepository;

  @Transactional
  public StockPriceSyncResult synchronize(LocalDate baseDate, List<StockPriceEntry> entries) {
    Map<String, Asset> listedStocksByCode =
        assetRepository.findAllByCategory(AssetCategory.STOCK).stream()
            .filter(Asset::isListed)
            .collect(Collectors.toMap(Asset::getAssetCode, Function.identity()));
    Map<Long, StockDailyPrice> existingPricesByAssetId = new HashMap<>();
    for (StockDailyPrice price : stockDailyPriceRepository.findAllByBaseDate(baseDate)) {
      existingPricesByAssetId.put(price.getAsset().getId(), price);
    }

    int createdCount = 0;
    int updatedCount = 0;
    int skippedCount = 0;
    List<StockDailyPrice> pricesToSave = new ArrayList<>();

    for (StockPriceEntry entry : entries) {
      Asset asset = listedStocksByCode.get(entry.assetCode());
      if (asset == null) {
        skippedCount++;
        continue;
      }

      StockDailyPrice existingPrice = existingPricesByAssetId.get(asset.getId());
      if (existingPrice == null) {
        pricesToSave.add(new StockDailyPrice(asset, baseDate, entry.closePrice()));
        createdCount++;
      } else if (existingPrice.synchronize(entry.closePrice())) {
        pricesToSave.add(existingPrice);
        updatedCount++;
      } else {
        skippedCount++;
      }
    }

    stockDailyPriceRepository.saveAll(pricesToSave);
    return new StockPriceSyncResult(
        baseDate, entries.size(), createdCount, updatedCount, skippedCount);
  }
}
