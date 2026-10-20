package org.grit.daynomy.investmentcalendar.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class InvestmentCalendarDataSyncSchedulerTest {

  @Mock InvestmentCalendarDataSyncService syncService;
  @Mock InvestmentCalendarPriceBackfillService priceBackfillService;
  @Mock TaskScheduler taskScheduler;
  @InjectMocks InvestmentCalendarDataSyncScheduler scheduler;

  @Test
  void queuesStartupWorkAndPreventsOverlappingSynchronization() {
    scheduler.synchronizeOnStartup();

    var task = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(task.capture(), any(Instant.class));
    then(syncService).shouldHaveNoInteractions();
    then(priceBackfillService).shouldHaveNoInteractions();
    doAnswer(
            invocation -> {
              scheduler.synchronize();
              return new InvestmentEventSyncResult(0, 0, 0);
            })
        .when(syncService)
        .synchronize(any(LocalDate.class));
    given(priceBackfillService.backfill(any(LocalDate.class)))
        .willReturn(new InvestmentCalendarPriceBackfillResult(0, 0, 0, 0, 0));

    task.getValue().run();
    scheduler.synchronize();

    then(syncService).should(times(2)).synchronize(any(LocalDate.class));
    then(priceBackfillService).should(times(2)).backfill(any(LocalDate.class));
  }
}
