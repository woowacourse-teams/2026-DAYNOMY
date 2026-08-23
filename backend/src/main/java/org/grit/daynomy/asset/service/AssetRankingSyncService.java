package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AssetRankingSyncService {

  private static final int LOOKBACK_DAYS = 10;

  private final PublicDataStockPriceClient publicDataStockPriceClient;
  private final AssetRankingPersistenceService assetRankingPersistenceService;

  public int syncKosdaqTopRankings() {
    return syncKosdaqTopRankings(LocalDate.now());
  }

  @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
  public void syncKosdaqTopRankingsDaily() {
    syncKosdaqTopRankings();
  }

  public int syncKosdaqTopRankings(LocalDate today) {
    for (int daysAgo = 0; daysAgo < LOOKBACK_DAYS; daysAgo++) {
      LocalDate requestedDate = today.minusDays(daysAgo);
      List<PublicDataStockPriceItem> items =
          extractItems(publicDataStockPriceClient.getKosdaqStockPrices(requestedDate));
      if (!items.isEmpty()) {
        return assetRankingPersistenceService.saveRankings(items);
      }
    }
    return 0;
  }

  private List<PublicDataStockPriceItem> extractItems(PublicDataStockPriceResponse response) {
    if (response.body().items() == null || response.body().items().item() == null) {
      return List.of();
    }
    return response.body().items().item();
  }
}
