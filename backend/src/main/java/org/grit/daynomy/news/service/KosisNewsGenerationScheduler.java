package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.kosis.enabled", havingValue = "true")
@Component
public class KosisNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.kosis.cron}", zone = "Asia/Seoul")
  public void generateKosisNews() {
    log.info("Starting scheduled KOSIS news generation");
    int savedCount = newsGenerationService.generateKosisNews();
    log.info("Finished scheduled KOSIS news generation: savedCount={}", savedCount);
  }
}
