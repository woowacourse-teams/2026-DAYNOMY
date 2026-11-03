package org.grit.daynomy.asset.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.calendar.sync.enabled", havingValue = "true")
@Component
public class MarketCalendarSyncScheduler {
  private final MarketCalendarSyncService service;
  private final TaskScheduler taskScheduler;

  @EventListener(ApplicationReadyEvent.class)
  public void synchronizeOnStartup() {
    taskScheduler.schedule(this::synchronize, Instant.now());
  }

  @Scheduled(cron = "${stock.calendar.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    service.synchronize();
  }
}
