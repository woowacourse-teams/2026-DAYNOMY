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

  private final AssetRepository assetRepository;

  @Transactional
  public StockSyncResult synchronize(LocalDate baseDate, List<StockMasterEntry> entries) {
    Map<String, Asset> existingByCode = new HashMap<>();
    for (Asset asset : assetRepository.findAllByCategory(AssetCategory.STOCK)) {
      existingByCode.put(asset.getAssetCode(), asset);
    }

    Set<String> synchronizedCodes = new HashSet<>();
    List<Asset> changedAssets = new ArrayList<>();
    int createdCount = 0;
    int updatedCount = 0;

    for (StockMasterEntry entry : entries) {
      synchronizedCodes.add(entry.code());
      Asset asset = existingByCode.get(entry.code());
      if (asset == null) {
        changedAssets.add(
            Asset.listedStock(
                entry.name(), entry.code(), entry.market(), entry.isinCode(), entry.baseDate()));
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
    for (Asset asset : existingByCode.values()) {
      if (!synchronizedCodes.contains(asset.getAssetCode()) && asset.delist()) {
        changedAssets.add(asset);
        delistedCount++;
      }
    }

    assetRepository.saveAll(changedAssets);
    return new StockSyncResult(baseDate, entries.size(), createdCount, updatedCount, delistedCount);
  }
}
