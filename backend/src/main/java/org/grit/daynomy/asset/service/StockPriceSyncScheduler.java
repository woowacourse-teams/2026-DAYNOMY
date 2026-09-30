package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.price.sync.enabled", havingValue = "true")
@Component
public class StockPriceSyncScheduler {

  private final StockPriceSyncService stockPriceSyncService;

  @Scheduled(cron = "${stock.price.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    stockPriceSyncService.synchronize();
  }
}
