package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.bok.enabled", havingValue = "true")
@Component
public class BokNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.bok.cron}", zone = "Asia/Seoul")
  public void generateBokNews() {
    log.info("Starting scheduled BOK news generation");
    int savedCount = newsGenerationService.generateBokNews();
    log.info("Finished scheduled BOK news generation: savedCount={}", savedCount);
  }
}
