package org.grit.daynomy.news.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.us.enabled", havingValue = "true")
@Component
public class UsEconomyNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.us.cron}", zone = "Asia/Seoul")
  public void generateUsEconomicNewsDrafts() {
    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.NEWS_GENERATION_STARTED.code())
        .addKeyValue("generationType", "us")
        .log(LogEvent.NEWS_GENERATION_STARTED.message());
    try {
      newsGenerationService.generateUsEconomicNewsDrafts();
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.NEWS_GENERATION_FAILED.code())
          .addKeyValue("generationType", "us")
          .addKeyValue("errorCode", exception.errorCode().code())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.NEWS_GENERATION_FAILED.message());
    } catch (RuntimeException exception) {
      log.atError()
          .setCause(SanitizedLogException.from(exception))
          .addKeyValue("event", LogEvent.NEWS_GENERATION_FAILED.code())
          .addKeyValue("generationType", "us")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.NEWS_GENERATION_FAILED.message());
    }
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
