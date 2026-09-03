package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.dart.enabled", havingValue = "true")
@Component
public class DartNewsGenerationScheduler {

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.dart.cron}", zone = "Asia/Seoul")
  public void generateDartNews() {
    newsGenerationService.generateScheduledDartNews();
  }
}
