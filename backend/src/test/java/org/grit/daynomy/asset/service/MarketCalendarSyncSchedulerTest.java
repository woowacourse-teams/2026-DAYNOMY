package org.grit.daynomy.asset.service;

import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

class MarketCalendarSyncSchedulerTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void startupAndScheduledRefreshRespectTheEnableFlag(boolean enabled) {
    var service = mock(MarketCalendarSyncService.class);
    var taskScheduler = mock(TaskScheduler.class);
    new ApplicationContextRunner()
        .withUserConfiguration(MarketCalendarSyncScheduler.class)
        .withBean(MarketCalendarSyncService.class, () -> service)
        .withBean(TaskScheduler.class, () -> taskScheduler)
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
                verifyNoInteractions(service);
                ArgumentCaptor<Runnable> synchronization = ArgumentCaptor.forClass(Runnable.class);
                verify(taskScheduler).schedule(synchronization.capture(), any(Instant.class));
                synchronization.getValue().run();
                verify(service).synchronize();
                context.getBean(MarketCalendarSyncScheduler.class).synchronize();
                verify(service, times(2)).synchronize();
              } else verifyNoInteractions(service, taskScheduler);
            });
  }
}
