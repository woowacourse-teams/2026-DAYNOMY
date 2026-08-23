package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
@Service
public class AssetRankingPersistenceService {

  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final AssetRepository assetRepository;
  private final AssetRankingHistoryRepository assetRankingHistoryRepository;

  public int saveRankings(List<PublicDataStockPriceItem> rankingItems) {
    if (rankingItems.isEmpty()) {
      return 0;
    }

    LocalDate rankedDate = parseBaseDate(rankingItems.get(0).basDt());
    assetRankingHistoryRepository.deleteByRankedDate(rankedDate);
    assetRankingHistoryRepository.flush();

    List<AssetRankingHistory> histories =
        java.util.stream.IntStream.range(0, rankingItems.size())
            .mapToObj(index -> toRankingHistory(index + 1, rankedDate, rankingItems.get(index)))
            .toList();

    assetRankingHistoryRepository.saveAll(histories);
    return histories.size();
  }

  private AssetRankingHistory toRankingHistory(
      int ranking, LocalDate rankedDate, PublicDataStockPriceItem item) {
    Asset asset = findOrCreateStockAsset(item);
    return new AssetRankingHistory(asset, ranking, rankedDate);
  }

  private Asset findOrCreateStockAsset(PublicDataStockPriceItem item) {
    return assetRepository
        .findByCategoryAndAssetCode(AssetCategory.STOCK, item.srtnCd())
        .orElseGet(
            () ->
                assetRepository.save(new Asset(item.itmsNm(), AssetCategory.STOCK, item.srtnCd())));
  }

  private LocalDate parseBaseDate(String baseDate) {
    return LocalDate.parse(baseDate, BASIC_DATE_FORMAT);
  }
}
