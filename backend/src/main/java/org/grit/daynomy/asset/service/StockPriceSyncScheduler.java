package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.price.sync.enabled", havingValue = "true")
@Component
public class StockPriceSyncScheduler {

  private final StockPriceSyncService stockPriceSyncService;

  @Scheduled(cron = "${stock.price.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    log.info("Starting scheduled domestic stock price synchronization");
    StockPriceSyncResult result = stockPriceSyncService.synchronize();
    log.info(
        "Finished scheduled domestic stock price synchronization: baseDate={}, receivedCount={}, createdCount={}, updatedCount={}, skippedCount={}",
        result.baseDate(),
        result.receivedCount(),
        result.createdCount(),
        result.updatedCount(),
        result.skippedCount());
  }
}
