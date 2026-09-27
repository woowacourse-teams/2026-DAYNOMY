package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockMasterPersistenceService {

  private static final Set<AssetCategory> SUPPORTED_CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);

  private final AssetRepository assetRepository;

  @Transactional
  public StockSyncResult synchronize(LocalDate baseDate, List<StockMasterEntry> entries) {
    Map<AssetKey, Asset> existingByKey = new HashMap<>();
    for (Asset asset : assetRepository.findAllByCategoryIn(SUPPORTED_CATEGORIES)) {
      existingByKey.put(new AssetKey(asset.getCategory(), asset.getAssetCode()), asset);
    }

    Set<AssetKey> synchronizedKeys = new HashSet<>();
    List<Asset> changedAssets = new ArrayList<>();
    int createdCount = 0;
    int updatedCount = 0;

    for (StockMasterEntry entry : entries) {
      AssetKey key = new AssetKey(entry.category(), entry.code());
      synchronizedKeys.add(key);
      Asset asset = existingByKey.get(key);
      if (asset == null) {
        changedAssets.add(
            Asset.listedSecurity(
                entry.name(),
                entry.category(),
                entry.code(),
                entry.market(),
                entry.isinCode(),
                entry.baseDate()));
        createdCount++;
        continue;
      }

      if (asset.synchronizeStock(
          entry.name(), entry.market(), entry.isinCode(), entry.baseDate())) {
        changedAssets.add(asset);
        updatedCount++;
      }
    }

    int delistedCount = 0;
    for (Asset asset : existingByKey.values()) {
      AssetKey key = new AssetKey(asset.getCategory(), asset.getAssetCode());
      if (!synchronizedKeys.contains(key) && asset.delist()) {
        changedAssets.add(asset);
        delistedCount++;
      }
    }

    assetRepository.saveAll(changedAssets);
    return new StockSyncResult(baseDate, entries.size(), createdCount, updatedCount, delistedCount);
  }

  private record AssetKey(AssetCategory category, String code) {}
}
