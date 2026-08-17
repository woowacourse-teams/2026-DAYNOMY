package org.grit.daynomy.news.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.generation.dart.enabled", havingValue = "true")
@Component
public class DartNewsGenerationScheduler {

  private static final String MAJOR_REPORT = "B";
  private static final String KOSPI = "Y";
  private static final String KOSDAQ = "K";

  private final NewsGenerationService newsGenerationService;

  @Scheduled(cron = "${news.generation.dart.cron}", zone = "Asia/Seoul")
  public void generateDartNews() {
    LocalDate today = LocalDate.now();
    log.info("Starting scheduled DART news generation for {}", today);
    int savedCount =
        newsGenerationService.generateDartNews(today, today, MAJOR_REPORT, KOSPI)
            + newsGenerationService.generateDartNews(today, today, MAJOR_REPORT, KOSDAQ);

    log.info("Finished scheduled DART news generation: savedCount={}, date={}", savedCount, today);
  }
}
