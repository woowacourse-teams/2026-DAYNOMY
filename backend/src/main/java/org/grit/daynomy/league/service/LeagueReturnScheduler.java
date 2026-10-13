package org.grit.daynomy.league.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.service.MarketCalendarSyncResult;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.grit.daynomy.common.logging.LogEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "league.calculation.enabled", havingValue = "true")
@Component
public class LeagueReturnScheduler {

  private final LeagueReturnService leagueReturnService;
  private boolean pendingFullRecalculation = true;
  private LocalDate pendingPriceFrom;

  @Scheduled(cron = "${league.calculation.cron}", zone = "Asia/Seoul")
  public synchronized void calculateReturns() {
    if (pendingFullRecalculation) {
      if (recalculateAfterSync(leagueReturnService::recalculate)) {
        pendingFullRecalculation = false;
        pendingPriceFrom = null;
      }
    } else if (pendingPriceFrom != null) {
      if (recalculateAfterSync(() -> leagueReturnService.recalculateFrom(pendingPriceFrom))) {
        pendingPriceFrom = null;
      }
    } else {
      recalculateAfterSync(leagueReturnService::recalculatePending);
    }
  }

  @EventListener(StockPriceSyncResult.class)
  public synchronized void recalculateAfterPriceSync(StockPriceSyncResult result) {
    if (pendingPriceFrom == null || result.earliestBaseDate().isBefore(pendingPriceFrom)) {
      pendingPriceFrom = result.earliestBaseDate();
    }
    if (recalculateAfterSync(() -> leagueReturnService.recalculateFrom(pendingPriceFrom))) {
      pendingPriceFrom = null;
    }
  }

  @EventListener(MarketCalendarSyncResult.class)
  public synchronized void recalculateAfterCalendarSync() {
    pendingFullRecalculation = true;
    if (recalculateAfterSync(leagueReturnService::recalculate)) {
      pendingFullRecalculation = false;
      pendingPriceFrom = null;
    }
  }

  private boolean recalculateAfterSync(Runnable calculation) {
    try {
      calculation.run();
      return true;
    } catch (RuntimeException exception) {
      // 집계 실패는 다음 예약 실행에서 재시도한다.
      log.atError()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_FAILED.code())
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .log(LogEvent.LEAGUE_CALCULATION_FAILED.message());
      return false;
    }
  }
}
