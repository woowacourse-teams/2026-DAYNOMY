package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetRankingSyncService {

  private static final int LOOKBACK_DAYS = 10;
  private static final int RANKING_LIMIT = 150;
  private static final String KOSDAQ = "KOSDAQ";
  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final PublicDataStockPriceClient publicDataStockPriceClient;
  private final AssetRepository assetRepository;
  private final AssetRankingHistoryRepository assetRankingHistoryRepository;

  @Transactional
  public int syncKosdaqTopRankings() {
    return syncKosdaqTopRankings(LocalDate.now());
  }

  @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
  @Transactional
  public void syncKosdaqTopRankingsDaily() {
    syncKosdaqTopRankings();
  }

  @Transactional
  public int syncKosdaqTopRankings(LocalDate today) {
    for (int daysAgo = 0; daysAgo < LOOKBACK_DAYS; daysAgo++) {
      LocalDate requestedDate = today.minusDays(daysAgo);
      List<PublicDataStockPriceItem> items =
          extractItems(publicDataStockPriceClient.getKosdaqStockPrices(requestedDate));
      if (!items.isEmpty()) {
        return saveRankings(items);
      }
    }
    return 0;
  }

  private int saveRankings(List<PublicDataStockPriceItem> items) {
    List<PublicDataStockPriceItem> rankingItems = rankingItems(items);
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

  private List<PublicDataStockPriceItem> rankingItems(List<PublicDataStockPriceItem> items) {
    Set<String> seenAssetCodes = new HashSet<>();
    return items.stream()
        .filter(item -> KOSDAQ.equals(item.mrktCtg()))
        .filter(item -> parseLong(item.mrktTotAmt()) > 0)
        .sorted(
            (first, second) ->
                Long.compare(parseLong(second.mrktTotAmt()), parseLong(first.mrktTotAmt())))
        .filter(item -> seenAssetCodes.add(item.srtnCd()))
        .limit(RANKING_LIMIT)
        .toList();
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

  private List<PublicDataStockPriceItem> extractItems(PublicDataStockPriceResponse response) {
    if (response.body().items() == null || response.body().items().item() == null) {
      return List.of();
    }
    return response.body().items().item();
  }

  private LocalDate parseBaseDate(String baseDate) {
    return LocalDate.parse(baseDate, BASIC_DATE_FORMAT);
  }

  private long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    return Long.parseLong(value.replace(",", ""));
  }
}
