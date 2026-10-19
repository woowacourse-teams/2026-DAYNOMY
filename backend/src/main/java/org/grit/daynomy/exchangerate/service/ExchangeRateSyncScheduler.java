package org.grit.daynomy.exchangerate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exchange-rate.sync.enabled", havingValue = "true")
public class ExchangeRateSyncScheduler {
  private final ExchangeRateService service;

  @Scheduled(cron = "${exchange-rate.sync.cron}", zone = "America/New_York")
  public void synchronize() {
    try {
      var quote = service.refreshToday();
      log.info(
          "USD/KRW synchronization complete: requestedDate={}, baseDate={}",
          quote.requestedDate(),
          quote.baseDate());
    } catch (RuntimeException failure) {
      // External client exceptions can contain credentials in their request URLs.
      log.warn("USD/KRW synchronization failed: type={}", failure.getClass().getSimpleName());
    }
  }
}
