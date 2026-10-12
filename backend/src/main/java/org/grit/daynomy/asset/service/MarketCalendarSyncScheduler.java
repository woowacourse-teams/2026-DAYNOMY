package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.calendar.sync.enabled", havingValue = "true")
@Component
public class MarketCalendarSyncScheduler {
  private final MarketCalendarSyncService service;

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "${stock.calendar.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    service.synchronize();
  }
}
