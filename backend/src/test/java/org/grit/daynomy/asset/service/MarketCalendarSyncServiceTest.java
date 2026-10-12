package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class MarketCalendarSyncServiceTest {
  @Mock private KrxCalendarClient client;
  @Mock private MarketTradingDayRepository repository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private ApplicationEventPublisher events;
  @InjectMocks private MarketCalendarSyncService service;
  @Captor private ArgumentCaptor<List<MarketTradingDay>> added;

  @Test
  void registersTheNextYearIncludingWeekendsAndLeapDayOnlyAfterPublication() {
    when(client.publishedYears()).thenReturn(Set.of(2028, 2030));
    when(client.holidays(2028)).thenReturn(Set.of(LocalDate.of(2028, 1, 1)));
    service.synchronize(LocalDate.of(2027, 12, 29));
    verify(repository).saveAll(added.capture());
    assertThat(added.getValue()).hasSize(366);
    assertThat(added.getValue())
        .filteredOn(d -> d.getBaseDate().equals(LocalDate.of(2028, 2, 29)))
        .singleElement()
        .satisfies(d -> assertThat(d.isTrading()).isTrue());
    assertThat(added.getValue())
        .filteredOn(d -> d.getBaseDate().equals(LocalDate.of(2028, 1, 1)))
        .singleElement()
        .satisfies(d -> assertThat(d.isTrading()).isFalse());
    verify(client, never()).holidays(2027);
    verify(client, never()).holidays(2030);
    verify(events).publishEvent(new MarketCalendarSyncResult(366));
  }

  @Test
  void revisesTemporaryHolidaysAndPreservesCollectionForUnchangedDays() {
    var first = LocalDate.of(2026, 1, 1);
    List<MarketTradingDay> existing =
        first
            .datesUntil(first.plusYears(1))
            .map(d -> new MarketTradingDay(d, d.getDayOfWeek().getValue() <= 5))
            .toList();
    MarketTradingDay unchanged = existing.get(0);
    Instant collected = Instant.parse("2026-10-13T09:00:00Z");
    unchanged.collected(collected);
    MarketTradingDay holiday =
        existing.stream()
            .filter(d -> d.getBaseDate().equals(LocalDate.of(2026, 10, 12)))
            .findFirst()
            .orElseThrow();
    holiday.collected(collected);
    when(client.publishedYears()).thenReturn(Set.of(2026));
    when(client.holidays(2026)).thenReturn(Set.of(holiday.getBaseDate()));
    when(repository.findCalendarForUpdate(first, first.plusYears(1).minusDays(1)))
        .thenReturn(existing);
    service.synchronize(first);
    assertThat(holiday.isTrading()).isFalse();
    assertThat(holiday.getCollectedAt()).isNull();
    assertThat(unchanged.getCollectedAt()).isEqualTo(collected);
    verify(events).publishEvent(new MarketCalendarSyncResult(1));
    service.synchronize(first);
    verifyNoMoreInteractions(events);
    verify(repository, never()).saveAll(any());
    assertThat(holiday.reviseTrading(true)).isTrue();
    assertThat(holiday.getCollectedAt()).isNull();
  }

  @Test
  void continuesOtherPublishedYearsAfterFailureAndDoesNotOverwriteFailedYear() {
    when(client.publishedYears()).thenReturn(Set.of(2025, 2026));
    when(client.holidays(2025)).thenThrow(new IllegalStateException("unavailable"));
    when(client.holidays(2026)).thenReturn(Set.of(LocalDate.of(2026, 1, 1)));
    service.synchronize(LocalDate.of(2026, 10, 11));
    verify(repository, never())
        .findCalendarForUpdate(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    verify(events).publishEvent(new MarketCalendarSyncResult(365));
  }

  @Test
  void leavesAllExistingDataUntouchedWhenPublicationCannotBeChecked() {
    when(client.publishedYears()).thenThrow(new IllegalStateException("unavailable"));
    service.synchronize(LocalDate.of(2026, 10, 11));
    verifyNoInteractions(repository, transactionManager, events);
  }
}
