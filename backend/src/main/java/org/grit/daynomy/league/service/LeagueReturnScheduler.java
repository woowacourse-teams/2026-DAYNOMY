package org.grit.daynomy.league.service;

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

  @Scheduled(cron = "${league.calculation.cron}", zone = "Asia/Seoul")
  public void calculateReturns() {
    // shortcut: 전체 이력을 재계산한다. 이력 증가로 지연되면 최신 거래일 증분 집계로 전환한다.
    leagueReturnService.recalculate();
  }

  @EventListener({StockPriceSyncResult.class, MarketCalendarSyncResult.class})
  public void recalculateAfterSync() {
    try {
      calculateReturns();
    } catch (RuntimeException exception) {
      // 원본 저장은 이미 커밋됐다. 집계 실패는 예약 집계에서 재시도한다.
      log.atError()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_FAILED.code())
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .log(LogEvent.LEAGUE_CALCULATION_FAILED.message());
    }
  }
}
