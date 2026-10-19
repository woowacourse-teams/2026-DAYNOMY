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
@ConditionalOnProperty(name = "news.generation.korea.enabled", havingValue = "true")
@Component
public class KoreanEconomyNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.korea.cron}", zone = "Asia/Seoul")
  public void generateKoreanEconomicNewsDrafts() {
    log.atDebug()
        .addKeyValue("event", LogEvent.NEWS_GENERATION_STARTED.code())
        .addKeyValue("generationType", "korea")
        .log(LogEvent.NEWS_GENERATION_STARTED.message());
    try {
      newsGenerationService.generateKoreanEconomicNewsDrafts();
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.NEWS_GENERATION_FAILED.code())
          .addKeyValue("generationType", "korea")
          .addKeyValue("errorCode", exception.errorCode().code())
          .log(LogEvent.NEWS_GENERATION_FAILED.message());
    }
  }
}
