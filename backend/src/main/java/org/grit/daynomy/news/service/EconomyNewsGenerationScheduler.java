package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.economy.enabled", havingValue = "true")
@Component
public class EconomyNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.economy.cron}", zone = "Asia/Seoul")
  public void generateEconomyNewsDrafts() {
    log.atDebug()
        .addKeyValue("event", LogEvent.NEWS_GENERATION_STARTED.code())
        .addKeyValue("generationType", "economy")
        .log(LogEvent.NEWS_GENERATION_STARTED.message());
    try {
      newsGenerationService.generateEconomyNewsDrafts();
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.NEWS_GENERATION_FAILED.code())
          .addKeyValue("generationType", "economy")
          .addKeyValue("errorCode", exception.errorCode().code())
          .log(LogEvent.NEWS_GENERATION_FAILED.message());
    }
  }
}
