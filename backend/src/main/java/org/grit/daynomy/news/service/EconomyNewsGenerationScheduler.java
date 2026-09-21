package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
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
    log.info("Starting scheduled economy news drafts generation");
    try {
      newsGenerationService.generateEconomyNewsDrafts();
    } catch (BusinessException exception) {
      log.warn(
          "Scheduled economy news drafts generation failed: errorCode={}",
          exception.errorCode().code());
    }
  }
}
