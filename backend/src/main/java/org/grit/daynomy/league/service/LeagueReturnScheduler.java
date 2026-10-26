package org.grit.daynomy.league.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.service.MarketCalendarSyncResult;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.grit.daynomy.common.logging.LogEvent;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "league.calculation.enabled", havingValue = "true")
@Component
public class LeagueReturnScheduler {

  private final LeagueReturnService leagueReturnService;
  private final TaskScheduler taskScheduler;
  private boolean pendingFullRecalculation = true;
  private LocalDate pendingPriceFrom;

  @EventListener(ApplicationReadyEvent.class)
  public void calculateOnStartup() {
    taskScheduler.schedule(this::calculateReturns, Instant.now());
  }

  @Scheduled(cron = "${league.calculation.cron}", zone = "Asia/Seoul")
  public synchronized void calculateReturns() {
    if (pendingFullRecalculation) {
      if (recalculateAfterSync("STARTUP_OR_SCHEDULE", leagueReturnService::recalculate)) {
        pendingFullRecalculation = false;
        pendingPriceFrom = null;
      }
    } else if (pendingPriceFrom != null) {
      if (recalculateAfterSync(
          "STARTUP_OR_SCHEDULE", () -> leagueReturnService.recalculateFrom(pendingPriceFrom))) {
        pendingPriceFrom = null;
      }
    } else {
      recalculateAfterSync("STARTUP_OR_SCHEDULE", leagueReturnService::recalculatePending);
    }
  }

  @EventListener(StockPriceSyncResult.class)
  public synchronized void recalculateAfterPriceSync(StockPriceSyncResult result) {
    if (pendingPriceFrom == null || result.earliestBaseDate().isBefore(pendingPriceFrom)) {
      pendingPriceFrom = result.earliestBaseDate();
    }
    if (recalculateAfterSync(
        "PRICE_SYNC", () -> leagueReturnService.recalculateFrom(pendingPriceFrom))) {
      pendingPriceFrom = null;
    }
  }

  @EventListener(MarketCalendarSyncResult.class)
  public synchronized void recalculateAfterCalendarSync() {
    pendingFullRecalculation = true;
    if (recalculateAfterSync("CALENDAR_SYNC", leagueReturnService::recalculate)) {
      pendingFullRecalculation = false;
      pendingPriceFrom = null;
    }
  }

  private boolean recalculateAfterSync(String trigger, Runnable calculation) {
    boolean generatedTrace = MDC.get("traceId") == null;
    if (generatedTrace) MDC.put("traceId", UUID.randomUUID().toString());
    long started = System.nanoTime();
    try {
      log.atDebug()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_TRIGGERED.code())
          .addKeyValue("trigger", trigger)
          .addKeyValue("pendingFullRecalculation", pendingFullRecalculation)
          .addKeyValue("pendingPriceFrom", pendingPriceFrom)
          .log(LogEvent.LEAGUE_CALCULATION_TRIGGERED.message());
      calculation.run();
      return true;
    } catch (RuntimeException exception) {
      // 집계 실패는 다음 예약 실행에서 재시도한다.
      log.atWarn()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_RETRY_PENDING.code())
          .addKeyValue("trigger", trigger)
          .addKeyValue("pendingFullRecalculation", pendingFullRecalculation)
          .addKeyValue("pendingPriceFrom", pendingPriceFrom)
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .log(LogEvent.LEAGUE_CALCULATION_RETRY_PENDING.message());
      return false;
    } finally {
      if (generatedTrace) MDC.remove("traceId");
    }
  }
}
