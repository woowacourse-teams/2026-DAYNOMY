package org.grit.daynomy.asset.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsStockKoreanNamePersistenceService {
  private static final Set<AssetCategory> CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);
  private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");
  private final AssetRepository assetRepository;

  @Transactional
  public UsStockKoreanNameSyncResult synchronize(List<KisStockName> entries) {
    Map<AssetKey, Asset> assets = new HashMap<>();
    for (Asset asset : assetRepository.findAllByCategoryIn(CATEGORIES)) {
      if (asset.isListed() && asset.getMarket() != null && asset.getMarket().isUs()) {
        assets.put(new AssetKey(asset.getCategory(), asset.getAssetCode()), asset);
      }
    }
    if (assets.isEmpty()) throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_REQUIRED);

    Map<Asset, String> names = new LinkedHashMap<>();
    int matched = 0;
    int unmatched = 0;
    int skipped = 0;
    for (KisStockName entry : entries) {
      if (entry.koreanName() == null || !HANGUL.matcher(entry.koreanName()).find()) {
        skipped++;
        continue;
      }
      List<Asset> candidates = candidates(entry, assets);
      if (candidates.size() != 1) {
        unmatched++;
        continue;
      }
      Asset asset = candidates.getFirst();
      String name = entry.koreanName().strip();
      String previous = names.putIfAbsent(asset, name);
      if (previous != null && !previous.equals(name)) {
        throw new BusinessException(AssetErrorCode.US_STOCK_KOREAN_NAME_DATA_INVALID);
      }
      matched++;
    }

    List<Asset> changed =
        names.entrySet().stream()
            .filter(entry -> entry.getKey().synchronizeKoreanName(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
    assetRepository.saveAll(changed);
    return new UsStockKoreanNameSyncResult(
        entries.size(), matched, changed.size(), unmatched, skipped);
  }

  private List<Asset> candidates(KisStockName entry, Map<AssetKey, Asset> assets) {
    AssetCategory category =
        switch (entry.securityType()) {
          case "2" -> AssetCategory.STOCK;
          case "3" -> AssetCategory.ETF;
          default -> null;
        };
    if (category == null) return List.of();
    Set<StockMarket> markets =
        switch (entry.exchangeCode()) {
          case "NAS" -> Set.of(StockMarket.NASDAQ);
          case "NYS" -> Set.of(StockMarket.NYSE);
          // KIS groups US ETF venues in AMS (for example, NYSE Arca's VTI and SPY).
          case "AMS" -> Set.of(StockMarket.NYSE_AMERICAN, StockMarket.NYSE_ARCA, StockMarket.BATS);
          default -> Set.of();
        };
    return java.util.stream.Stream.of(
            entry.symbol(), entry.symbol().replace('/', '.'), entry.symbol().replace('/', '-'))
        .distinct()
        .map(symbol -> assets.get(new AssetKey(category, symbol + ".US")))
        .filter(java.util.Objects::nonNull)
        .filter(asset -> markets.contains(asset.getMarket()))
        .toList();
  }

  private record AssetKey(AssetCategory category, String assetCode) {}
}
