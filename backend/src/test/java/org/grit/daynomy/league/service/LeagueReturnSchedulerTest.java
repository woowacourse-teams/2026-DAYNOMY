package org.grit.daynomy.league.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.asset.service.MarketCalendarSyncResult;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;

class LeagueReturnSchedulerTest {
  @Test
  void startupQueuesFullRecalculationWithoutBlockingApplicationReadyEvent() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    TaskScheduler taskScheduler = mock(TaskScheduler.class);
    new ApplicationContextRunner()
        .withUserConfiguration(LeagueReturnScheduler.class)
        .withBean(LeagueReturnService.class, () -> service)
        .withBean(TaskScheduler.class, () -> taskScheduler)
        .withPropertyValues("league.calculation.enabled=true")
        .run(
            context -> {
              context.publishEvent(
                  new ApplicationReadyEvent(
                      new SpringApplication(),
                      new String[0],
                      mock(ConfigurableApplicationContext.class),
                      Duration.ZERO));
              verifyNoInteractions(service);
              ArgumentCaptor<Runnable> calculation = ArgumentCaptor.forClass(Runnable.class);
              verify(taskScheduler).schedule(calculation.capture(), any(Instant.class));
              calculation.getValue().run();
              verify(service).recalculate();
              context.getBean(LeagueReturnScheduler.class).calculateReturns();
              verify(service).recalculatePending();
            });
  }

  @Test
  void failedBackgroundStartupCalculationIsRetriedByScheduledRun() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    TaskScheduler taskScheduler = mock(TaskScheduler.class);
    doThrow(new IllegalStateException("startup failure")).doReturn(1).when(service).recalculate();
    var scheduler = new LeagueReturnScheduler(service, taskScheduler);

    scheduler.calculateOnStartup();
    ArgumentCaptor<Runnable> calculation = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).schedule(calculation.capture(), any(Instant.class));
    calculation.getValue().run();
    scheduler.calculateReturns();
    scheduler.calculateReturns();

    verify(service, times(2)).recalculate();
    verify(service).recalculatePending();
  }

  @Test
  void calculationFailureDoesNotReportCommittedPricesAsFailedAndScheduledRetryStillWorks() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    doThrow(new IllegalStateException("calculation failure"))
        .doThrow(new IllegalStateException("scheduled failure"))
        .doReturn(1)
        .when(service)
        .recalculateFrom(any(LocalDate.class));
    var scheduler = new LeagueReturnScheduler(service, mock(TaskScheduler.class));
    scheduler.calculateReturns();
    LocalDate corrected = LocalDate.of(2026, 9, 18);
    scheduler.recalculateAfterPriceSync(
        new StockPriceSyncResult(LocalDate.of(2026, 9, 21), 2, 2, 0, 0, corrected));
    scheduler.calculateReturns();
    scheduler.calculateReturns();
    scheduler.calculateReturns();
    verify(service).recalculate();
    verify(service, times(3)).recalculateFrom(corrected);
    verify(service).recalculatePending();
  }

  @Test
  void failedScheduledFullRecalculationIsRetried() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    doThrow(new IllegalStateException("scheduled failure")).doReturn(1).when(service).recalculate();
    var scheduler = new LeagueReturnScheduler(service, mock(TaskScheduler.class));

    scheduler.calculateReturns();
    scheduler.calculateReturns();
    scheduler.calculateReturns();

    verify(service, times(2)).recalculate();
    verify(service).recalculatePending();
  }

  @Test
  void failedCalendarRecalculationIsRetriedByScheduledRun() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    doThrow(new IllegalStateException("calendar failure")).doReturn(1).when(service).recalculate();
    var scheduler = new LeagueReturnScheduler(service, mock(TaskScheduler.class));

    scheduler.recalculateAfterCalendarSync();
    scheduler.calculateReturns();
    scheduler.calculateReturns();

    verify(service, times(2)).recalculate();
    verify(service).recalculatePending();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void committedPricesTriggerAggregationOnlyWhenLeagueCalculationIsEnabled(boolean enabled) {
    LeagueReturnService service = mock(LeagueReturnService.class);
    new ApplicationContextRunner()
        .withUserConfiguration(LeagueReturnScheduler.class)
        .withBean(LeagueReturnService.class, () -> service)
        .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
        .withPropertyValues("league.calculation.enabled=" + enabled)
        .run(
            context -> {
              context.publishEvent(new StockPriceSyncResult(LocalDate.now(), 2, 2, 0, 0));
              context.publishEvent(new MarketCalendarSyncResult(1));
              if (enabled) {
                verify(service).recalculateFrom(any(LocalDate.class));
                verify(service).recalculate();
              } else verifyNoInteractions(service);
            });
  }
}
