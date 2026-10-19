package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "portfolio.snapshot.enabled", havingValue = "true")
@Component
public class PortfolioSnapshotScheduler {

  private final PortfolioSnapshotService snapshotService;

  @Scheduled(cron = "${portfolio.snapshot.cron}", zone = "Asia/Seoul")
  public void createSnapshots() {
    try {
      snapshotService.createLatestSnapshots();
    } catch (RuntimeException exception) {
      // 개별 생성 이전의 조회 실패를 기록하고 기존 예외 전파 동작을 유지한다.
      log.atError()
          .setCause(SanitizedLogException.from(exception))
          .addKeyValue("event", LogEvent.PORTFOLIO_SNAPSHOT_FAILED.code())
          .log("예약 포트폴리오 스냅샷 갱신 실패");
      throw exception;
    }
  }
}
