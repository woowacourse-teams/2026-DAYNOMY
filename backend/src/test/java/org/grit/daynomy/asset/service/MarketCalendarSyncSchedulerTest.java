package org.grit.daynomy.asset.service;

import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketCalendarSyncSchedulerTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void startupAndScheduledRefreshRespectTheEnableFlag(boolean enabled) {
    var service = mock(MarketCalendarSyncService.class);
    new ApplicationContextRunner()
        .withUserConfiguration(MarketCalendarSyncScheduler.class)
        .withBean(MarketCalendarSyncService.class, () -> service)
        .withPropertyValues(
            "stock.calendar.sync.enabled=" + enabled, "stock.calendar.sync.cron=0 5 6,18 * * *")
        .run(
            context -> {
              context.publishEvent(
                  new ApplicationReadyEvent(
                      new SpringApplication(),
                      new String[0],
                      context.getSourceApplicationContext(),
                      Duration.ZERO));
              if (enabled) {
                verify(service).synchronize();
                context.getBean(MarketCalendarSyncScheduler.class).synchronize();
                verify(service, times(2)).synchronize();
              } else verifyNoInteractions(service);
            });
  }
}
