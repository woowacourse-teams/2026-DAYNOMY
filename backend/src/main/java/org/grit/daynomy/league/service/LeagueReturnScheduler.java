package org.grit.daynomy.league.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "league.calculation.enabled", havingValue = "true")
@Component
public class LeagueReturnScheduler {

  private final LeagueReturnService leagueReturnService;

  @Scheduled(cron = "${league.calculation.cron}", zone = "Asia/Seoul")
  public void calculateReturns() {
    leagueReturnService.recalculate();
  }
}
