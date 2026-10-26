package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.us.sync.enabled", havingValue = "true")
public class UsStockSyncScheduler {
  private final UsStockSyncService service;

  @Scheduled(cron = "${stock.us.sync.cron}", zone = "America/New_York")
  public void synchronize() {
    StockSyncResult master = service.synchronizeMaster();
    StockPriceSyncResult prices = service.synchronizePrices(null);
    log.info(
        "US stock synchronization complete: symbols={}, priceDate={}, created={}, updated={}",
        master.syncedCount(),
        prices.baseDate(),
        prices.createdCount(),
        prices.updatedCount());
  }
}
