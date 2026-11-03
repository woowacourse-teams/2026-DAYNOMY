package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
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
class UsMarketCalendarSyncServiceTest {
  @Mock private EodhdClient client;
  @Mock private MarketTradingDayRepository days;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private ApplicationEventPublisher events;
  @InjectMocks private UsMarketCalendarSyncService service;
  @Captor private ArgumentCaptor<List<MarketTradingDay>> added;

  private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
  private final LocalDate first = LocalDate.of(today.getYear() - 1, 1, 1);
  private final LocalDate last = LocalDate.of(today.getYear() + 1, 12, 31);

  @Test
  void repairsMissingUsFlagsEvenWhenTodayIsAlreadyInitialized() {
    givenValidYearlyHolidays();
    var calendar = new ArrayList<>(calendar(first, last));
    calendar.set(0, new MarketTradingDay(first, false));
    when(days.findAllByBaseDateBetweenOrderByBaseDate(first, last)).thenReturn(calendar);

    service.initialize();

    verify(client, times(3)).getUsMarketHolidays(any(), any());
    verify(events).publishEvent(new MarketCalendarSyncResult(calendar.size()));
  }

  @Test
  void repairsMissingDatesEvenWhenAllExistingUsFlagsArePresent() {
    givenValidYearlyHolidays();
    var calendar = new ArrayList<>(calendar(first, last));
    calendar.remove(0);
    when(days.findAllByBaseDateBetweenOrderByBaseDate(first, last)).thenReturn(calendar);

    service.initialize();

    verify(client, times(3)).getUsMarketHolidays(any(), any());
  }

  @Test
  void skipsStartupCollectionOnlyWhenTheEntireRequiredRangeIsComplete() {
    when(days.findAllByBaseDateBetweenOrderByBaseDate(first, last))
        .thenReturn(calendar(first, last));

    service.initialize();

    verifyNoInteractions(client, transactionManager, events);
  }

  @Test
  void fillsWeekendsAndHolidaysAndIncludesLeapDay() {
    LocalDate from = LocalDate.of(2028, 1, 1);
    LocalDate to = LocalDate.of(2028, 12, 31);
    LocalDate holiday = LocalDate.of(2028, 7, 4);

    assertThat(service.saveYear(from, to, Set.of(holiday))).isEqualTo(366);

    verify(days).saveAll(added.capture());
    assertThat(added.getValue()).hasSize(366);
    assertThat(added.getValue())
        .filteredOn(day -> day.getBaseDate().equals(LocalDate.of(2028, 2, 29)))
        .singleElement()
        .satisfies(day -> assertThat(day.getUsTrading()).isTrue());
    assertThat(added.getValue())
        .filteredOn(day -> day.getBaseDate().equals(holiday) || day.getBaseDate().equals(from))
        .allSatisfy(day -> assertThat(day.getUsTrading()).isFalse());
  }

  @Test
  void notifiesRecalculationAfterCommitsOnlyForChangesAndPreservesKoreanCollection() {
    givenValidYearlyHolidays();
    int year = today.getYear();
    for (int target = year - 1; target <= year + 1; target++) {
      LocalDate from = LocalDate.of(target, 1, 1);
      LocalDate to = LocalDate.of(target, 12, 31);
      when(days.findCalendarForUpdate(from, to)).thenReturn(calendar(from, to));
    }
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);
    var current = calendar(from, to);
    MarketTradingDay holiday =
        current.stream().filter(day -> day.getUsTrading()).findFirst().orElseThrow();
    MarketTradingDay unchanged =
        current.stream()
            .filter(day -> day.getUsTrading() && day != holiday)
            .findFirst()
            .orElseThrow();
    Instant collected = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
    holiday.collected(collected);
    holiday.usCollected(collected);
    unchanged.collected(collected);
    unchanged.usCollected(collected);
    when(days.findCalendarForUpdate(from, to)).thenReturn(current);
    when(client.getUsMarketHolidays(from, to)).thenReturn(Set.of(from, holiday.getBaseDate()));

    service.synchronize();

    assertThat(holiday.getUsTrading()).isFalse();
    assertThat(holiday.getUsCollectedAt()).isNull();
    assertThat(holiday.isTrading()).isTrue();
    assertThat(holiday.getCollectedAt()).isEqualTo(collected);
    assertThat(unchanged.getUsCollectedAt()).isEqualTo(collected);
    var order = inOrder(transactionManager, events);
    order.verify(transactionManager, times(3)).commit(any());
    order.verify(events).publishEvent(new MarketCalendarSyncResult(1));

    service.synchronize();

    verifyNoMoreInteractions(events);
    verify(days, never()).saveAll(any());
  }

  @Test
  void leavesUnknownDaysUntouchedOnProviderFailureAndRetriesTheNextRun() {
    when(client.getUsMarketHolidays(any(), any()))
        .thenThrow(new IllegalStateException("unavailable"));

    service.synchronize();

    verifyNoInteractions(days, transactionManager, events);
    givenValidYearlyHolidays();

    service.synchronize();

    verify(client, times(6)).getUsMarketHolidays(any(), any());
    verify(events).publishEvent(new MarketCalendarSyncResult(calendar(first, last).size()));
  }

  @Test
  void continuesOtherYearsWhenOneYearFails() {
    givenValidYearlyHolidays();
    when(client.getUsMarketHolidays(first, first.plusYears(1).minusDays(1)))
        .thenThrow(new IllegalStateException("unavailable"));

    service.synchronize();

    verify(days, never()).findCalendarForUpdate(eq(first), any());
    verify(transactionManager, times(2)).commit(any());
    verify(events)
        .publishEvent(new MarketCalendarSyncResult(calendar(first.plusYears(1), last).size()));
  }

  @Test
  void doesNotPublishRecalculationWhenTransactionsFail() {
    givenValidYearlyHolidays();
    doThrow(new IllegalStateException("commit failed")).when(transactionManager).commit(any());

    service.synchronize();

    verify(transactionManager, times(3)).commit(any());
    verifyNoInteractions(events);
  }

  @Test
  void rejectsEmptyYearlyHolidaysWithoutSideEffectsAndRecoversOnRetry() {
    when(client.getUsMarketHolidays(any(), any())).thenReturn(Set.of());

    service.synchronize();

    verify(client, times(3)).getUsMarketHolidays(any(), any());
    verifyNoInteractions(days, transactionManager, events);

    givenValidYearlyHolidays();
    service.synchronize();

    verify(client, times(6)).getUsMarketHolidays(any(), any());
    verify(transactionManager, times(3)).commit(any());
    verify(events).publishEvent(new MarketCalendarSyncResult(calendar(first, last).size()));
  }

  @Test
  void continuesOtherYearsWhenOneYearReturnsEmptyHolidays() {
    givenValidYearlyHolidays();
    doReturn(Set.of()).when(client).getUsMarketHolidays(first, first.plusYears(1).minusDays(1));

    service.synchronize();

    verify(days, never()).findCalendarForUpdate(eq(first), any());
    verify(transactionManager, times(2)).commit(any());
    verify(events)
        .publishEvent(new MarketCalendarSyncResult(calendar(first.plusYears(1), last).size()));
  }

  private void givenValidYearlyHolidays() {
    doAnswer(invocation -> Set.of(((LocalDate) invocation.getArgument(0)).withDayOfYear(1)))
        .when(client)
        .getUsMarketHolidays(any(), any());
  }

  private List<MarketTradingDay> calendar(LocalDate from, LocalDate to) {
    return from.datesUntil(to.plusDays(1))
        .map(
            date -> {
              boolean trading = date.getDayOfWeek().getValue() <= 5 && date.getDayOfYear() != 1;
              var day = new MarketTradingDay(date, trading);
              day.reviseUsTrading(trading);
              return day;
            })
        .toList();
  }
}
