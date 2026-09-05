package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AssetRankingSyncService {

  private static final int LOOKBACK_DAYS = 10;
  private static final int RANKING_LIMIT = 150;
  private static final String KOSDAQ = "KOSDAQ";

  private final PublicDataStockPriceClient publicDataStockPriceClient;
  private final AssetRankingPersistenceService assetRankingPersistenceService;
  private final ReentrantLock syncLock = new ReentrantLock();

  public int syncKosdaqTopRankings() {
    return syncKosdaqTopRankings(LocalDate.now());
  }

  @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
  public void syncKosdaqTopRankingsDaily() {
    if (!syncLock.tryLock()) {
      log.info(
          "Skip scheduled KOSDAQ top rankings synchronization because another sync is running");
      return;
    }
    try {
      syncKosdaqTopRankingsInternal(LocalDate.now());
    } finally {
      syncLock.unlock();
    }
  }

  public int syncKosdaqTopRankings(LocalDate today) {
    if (!syncLock.tryLock()) {
      throw new BusinessException(AssetErrorCode.ASSET_RANKING_SYNC_ALREADY_RUNNING);
    }
    try {
      return syncKosdaqTopRankingsInternal(today);
    } finally {
      syncLock.unlock();
    }
  }

  private int syncKosdaqTopRankingsInternal(LocalDate today) {
    for (int daysAgo = 0; daysAgo < LOOKBACK_DAYS; daysAgo++) {
      LocalDate requestedDate = today.minusDays(daysAgo);
      List<PublicDataStockPriceItem> items =
          extractItems(publicDataStockPriceClient.getKosdaqStockPrices(requestedDate));
      List<PublicDataStockPriceItem> rankingItems = rankingItems(items);
      if (!rankingItems.isEmpty()) {
        return assetRankingPersistenceService.saveRankings(rankingItems);
      }
    }
    return 0;
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

  private List<PublicDataStockPriceItem> extractItems(PublicDataStockPriceResponse response) {
    if (response.body().items() == null || response.body().items().item() == null) {
      return List.of();
    }
    return response.body().items().item();
  }

  private long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    return Long.parseLong(value.replace(",", ""));
  }
}
