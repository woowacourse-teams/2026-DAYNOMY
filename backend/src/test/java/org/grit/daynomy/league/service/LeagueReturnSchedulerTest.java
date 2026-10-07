package org.grit.daynomy.league.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeagueReturnSchedulerTest {
  @Test
  void calculationFailureDoesNotReportCommittedPricesAsFailedAndScheduledRetryStillWorks() {
    LeagueReturnService service = mock(LeagueReturnService.class);
    doThrow(new IllegalStateException("calculation failure"))
        .doReturn(1)
        .when(service)
        .recalculate();
    var scheduler = new LeagueReturnScheduler(service);
    scheduler.pricesSynchronized(new StockPriceSyncResult(LocalDate.now(), 2, 2, 0, 0));
    scheduler.calculateReturns();
    verify(service, times(2)).recalculate();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void committedPricesTriggerAggregationOnlyWhenLeagueCalculationIsEnabled(boolean enabled) {
    LeagueReturnService service = mock(LeagueReturnService.class);
    new ApplicationContextRunner()
        .withUserConfiguration(LeagueReturnScheduler.class)
        .withBean(LeagueReturnService.class, () -> service)
        .withPropertyValues("league.calculation.enabled=" + enabled)
        .run(
            context -> {
              context.publishEvent(new StockPriceSyncResult(LocalDate.now(), 2, 2, 0, 0));
              if (enabled) verify(service).recalculate();
              else verifyNoInteractions(service);
            });
  }
}
