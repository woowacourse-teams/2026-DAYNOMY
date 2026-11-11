package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InvestmentCalendarDataSyncServiceTest {
  private final LocalDate today = LocalDate.of(2026, 10, 1);
  private final InvestmentCalendarProvider first = mock(InvestmentCalendarProvider.class);
  private final InvestmentCalendarProvider failed = mock(InvestmentCalendarProvider.class);
  private final InvestmentCalendarProvider last = mock(InvestmentCalendarProvider.class);
  private final InvestmentEventSyncService persistence = mock(InvestmentEventSyncService.class);

  @Test
  void isolatesFetchFailureAndAggregatesSuccessfulProviders() {
    var firstEntries = List.of(mock(InvestmentEventEntry.class));
    var lastEntries = List.of(mock(InvestmentEventEntry.class));
    when(first.fetch(today, 5, 30)).thenReturn(firstEntries);
    when(failed.fetch(today, 5, 30)).thenThrow(new IllegalStateException("unavailable"));
    when(last.fetch(today, 5, 30)).thenReturn(lastEntries);
    when(persistence.synchronize(firstEntries)).thenReturn(new InvestmentEventSyncResult(1, 2, 3));
    when(persistence.synchronize(lastEntries)).thenReturn(new InvestmentEventSyncResult(4, 5, 6));
    assertThat(service().synchronize(today)).isEqualTo(new InvestmentEventSyncResult(5, 7, 9));
    verify(persistence).synchronize(lastEntries);
  }

  @Test
  void continuesAfterPersistenceFailureWithoutAddingFailedCounts() {
    var entries = List.of(mock(InvestmentEventEntry.class));
    when(first.fetch(today, 5, 30)).thenReturn(entries);
    when(persistence.synchronize(entries)).thenThrow(new IllegalStateException("database failure"));
    when(failed.fetch(today, 5, 30)).thenReturn(List.of());
    when(last.fetch(today, 5, 30)).thenReturn(List.of());
    when(persistence.synchronize(List.of())).thenReturn(new InvestmentEventSyncResult(0, 0, 0));
    assertThat(service().synchronize(today)).isEqualTo(new InvestmentEventSyncResult(0, 0, 0));
    verify(last).fetch(today, 5, 30);
    verify(persistence, times(2)).synchronize(List.of());
  }

  @Test
  void emptyProvidersReturnZeroWithoutPersistence() {
    assertThat(new InvestmentCalendarDataSyncService(List.of(), persistence).synchronize(today))
        .isEqualTo(new InvestmentEventSyncResult(0, 0, 0));
    verifyNoInteractions(persistence);
  }

  private InvestmentCalendarDataSyncService service() {
    var service = new InvestmentCalendarDataSyncService(List.of(first, failed, last), persistence);
    ReflectionTestUtils.setField(service, "historyYears", 5);
    ReflectionTestUtils.setField(service, "dartLookbackDays", 30);
    return service;
  }
}
