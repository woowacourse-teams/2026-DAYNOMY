package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.sync.enabled", havingValue = "true")
@Component
public class StockMasterSyncScheduler {

  private final StockMasterSyncService stockMasterSyncService;

  @Scheduled(cron = "${stock.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    log.info("Starting scheduled domestic stock synchronization");
    StockSyncResult result = stockMasterSyncService.synchronize();
    log.info(
        "Finished scheduled domestic stock synchronization: baseDate={}, syncedCount={}, createdCount={}, updatedCount={}, delistedCount={}",
        result.baseDate(),
        result.syncedCount(),
        result.createdCount(),
        result.updatedCount(),
        result.delistedCount());
  }
}
