package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "portfolio.snapshot.enabled", havingValue = "true")
@Component
public class PortfolioSnapshotScheduler {

  private final PortfolioSnapshotService snapshotService;

  @Scheduled(cron = "${portfolio.snapshot.cron}", zone = "Asia/Seoul")
  public void createSnapshots() {
    snapshotService.createLatestSnapshots();
  }
}
