package org.grit.daynomy.investmentcalendar.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "investment-calendar.sync.enabled", havingValue = "true")
@Component
public class InvestmentCalendarDataSyncScheduler {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final InvestmentCalendarDataSyncService syncService;
  private final InvestmentCalendarPriceBackfillService priceBackfillService;
  private final AtomicBoolean running = new AtomicBoolean();

  @EventListener(ApplicationReadyEvent.class)
  public void synchronizeOnStartup() {
    synchronize();
  }

  @Scheduled(cron = "${investment-calendar.sync.cron}", zone = "Asia/Seoul")
  public void synchronize() {
    if (!running.compareAndSet(false, true)) {
      log.info("Skipping investment calendar synchronization because it is already running");
      return;
    }
    try {
      InvestmentEventSyncResult result = syncService.synchronize(LocalDate.now(SEOUL));
      log.info(
          "Finished investment calendar synchronization: createdCount={}, updatedCount={}, unchangedCount={}",
          result.createdCount(),
          result.updatedCount(),
          result.unchangedCount());
      backfillPrices();
    } finally {
      running.set(false);
    }
  }

  private void backfillPrices() {
    try {
      InvestmentCalendarPriceBackfillResult result =
          priceBackfillService.backfill(LocalDate.now(SEOUL));
      log.info(
          "Finished investment calendar price backfill: assetCount={}, fetchedCount={}, createdCount={}, updatedCount={}, unchangedCount={}",
          result.assetCount(),
          result.fetchedCount(),
          result.createdCount(),
          result.updatedCount(),
          result.unchangedCount());
    } catch (RuntimeException exception) {
      log.warn(
          "Investment calendar price backfill failed: errorType={}",
          exception.getClass().getSimpleName());
    }
  }
}
