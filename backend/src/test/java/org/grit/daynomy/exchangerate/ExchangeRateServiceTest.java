package org.grit.daynomy.exchangerate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.external.eodhd.*;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.junit.jupiter.api.Test;

class ExchangeRateServiceTest {
  @Test
  void weekendUsesEarlierRateAndCachesRequestedDateWithoutFutureLookahead() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var service = new ExchangeRateService(repository, client, java.time.Clock.systemUTC());
    var date = LocalDate.of(2020, 10, 18);
    when(client.getUsdKrwRates(any(), any()))
        .thenReturn(
            List.of(
                row("2020-10-15", "1300"),
                row("2020-10-16", "1310.123456"),
                row("2020-10-19", "9999")));
    var quote = service.quote(date);
    assertThat(quote.baseDate()).isEqualTo(LocalDate.of(2020, 10, 16));
    assertThat(quote.rate()).isEqualByComparingTo("1310.123456");
    when(repository.findById(date))
        .thenReturn(Optional.of(new UsdKrwRate(date, quote.baseDate(), quote.rate())));
    service.quote(date);
    verify(client, times(1)).getUsdKrwRates(any(), any());
  }

  @Test
  void missingNonPositiveAndFutureOnlyRatesCannotBecomeOne() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var service = new ExchangeRateService(repository, client, java.time.Clock.systemUTC());
    when(client.getUsdKrwRates(any(), any()))
        .thenReturn(List.of(row("2020-10-16", "0"), row("2020-10-19", "1400")));
    assertThatThrownBy(() -> service.quote(LocalDate.of(2020, 10, 18)))
        .isInstanceOf(BusinessException.class);
    verify(repository, never()).upsert(any(), any(), any(), any());
    assertThatThrownBy(() -> service.quote(LocalDate.of(2999, 1, 1)))
        .isInstanceOf(BusinessException.class);
  }

  private EodhdDailyPrice row(String date, String rate) {
    return new EodhdDailyPrice(null, null, LocalDate.parse(date), new BigDecimal(rate));
  }

  @Test
  void scheduledRefreshBypassesFreshCacheAndPreservesExistingRateOnFailure() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var service = new ExchangeRateService(repository, client, java.time.Clock.systemUTC());
    var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    var cutoff = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);
    var cached = new UsdKrwRate(today, cutoff.minusDays(1), new BigDecimal("1300"));
    when(repository.findById(today)).thenReturn(Optional.of(cached));
    when(client.getUsdKrwRates(cutoff.minusDays(14), cutoff))
        .thenReturn(List.of(row(cutoff.toString(), "1400")))
        .thenThrow(new IllegalStateException("unavailable"));

    assertThat(service.quote(today).rate()).isEqualByComparingTo("1300");
    verifyNoInteractions(client);
    var refreshed = service.refreshToday();
    assertThat(refreshed.requestedDate()).isEqualTo(today);
    assertThat(refreshed.baseDate()).isEqualTo(cutoff);
    assertThat(refreshed.rate()).isEqualByComparingTo("1400");

    clearInvocations(repository);
    assertThatThrownBy(service::refreshToday).isInstanceOf(IllegalStateException.class);
    verify(repository, never()).upsert(any(), any(), any(), any());
    assertThat(cached.getRate()).isEqualByComparingTo("1300");
  }

  @Test
  void provisionalRateIsRefreshedEvenWhenRequestedDateHasBecomeHistorical() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var date = LocalDate.of(2026, 10, 16);
    var clock =
        java.time.Clock.fixed(
            java.time.Instant.parse("2026-10-23T03:00:00Z"), java.time.ZoneOffset.UTC);
    var service = new ExchangeRateService(repository, client, clock);
    when(repository.findById(date))
        .thenReturn(
            Optional.of(
                new UsdKrwRate(
                    date,
                    date.minusDays(2),
                    new BigDecimal("1300"),
                    java.time.Instant.parse("2026-10-16T00:00:00Z"))));
    when(client.getUsdKrwRates(date.minusDays(14), date))
        .thenReturn(List.of(row("2026-10-16", "1400")));
    assertThat(service.quote(date).rate()).isEqualByComparingTo("1400");
    verify(repository)
        .upsert(eq(date), eq(date), eq(new BigDecimal("1400.000000")), eq(clock.instant()));
  }

  @Test
  void fallbackStoredAfterTheRequestedDayMustStillBeRefreshed() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var date = LocalDate.of(2026, 10, 19);
    var now = java.time.Instant.parse("2026-10-23T03:00:00Z");
    var clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC);
    var service = new ExchangeRateService(repository, client, clock);
    when(repository.findById(date))
        .thenReturn(
            Optional.of(
                new UsdKrwRate(
                    date,
                    date.minusDays(3),
                    new BigDecimal("1300"),
                    java.time.Instant.parse("2026-10-22T03:00:00Z"))));
    when(client.getUsdKrwRates(date.minusDays(14), date))
        .thenReturn(List.of(row("2026-10-19", "1400")));
    assertThat(service.quote(date).baseDate()).isEqualTo(date);
    assertThat(service.quote(date).rate()).isEqualByComparingTo("1400");
  }

  @Test
  void emptyResultsAreCachedButTransientFailuresArePropagatedAndRetried() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var clock = mock(java.time.Clock.class);
    when(clock.withZone(any()))
        .thenAnswer(i -> java.time.Clock.fixed(clock.instant(), i.getArgument(0)));
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-10-20T03:00:00Z"));
    var service = new ExchangeRateService(repository, client, clock);
    var date = LocalDate.of(2020, 10, 16);
    when(client.getUsdKrwRates(any(), any())).thenReturn(List.of());
    assertThat(service.ratesForDates(List.of(date, date))).isEmpty();
    assertThat(service.ratesForDates(List.of(date))).isEmpty();
    verify(client, times(1)).getUsdKrwRates(any(), any());
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-10-20T09:00:01Z"));
    var failure =
        new BusinessException(org.grit.daynomy.external.ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    when(client.getUsdKrwRates(any(), any()))
        .thenThrow(failure)
        .thenReturn(List.of(row("2020-10-16", "1400")));
    assertThatThrownBy(() -> service.ratesForDates(List.of(date))).isSameAs(failure);
    assertThat(service.ratesForDates(List.of(date)))
        .containsEntry(date, new BigDecimal("1400.000000"));
    verify(client, times(3)).getUsdKrwRates(any(), any());
  }

  @Test
  void rejectsUnsupportedDatesBeforeCallingProvider() {
    var repository = mock(UsdKrwRateRepository.class);
    var client = mock(EodhdClient.class);
    var service = new ExchangeRateService(repository, client, java.time.Clock.systemUTC());
    assertThatThrownBy(() -> service.quote(LocalDate.of(1999, 12, 31)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(repository, client);
  }

  @Test
  void publicRequestBudgetRecoversAfterOneMinute() {
    var clock = mock(java.time.Clock.class);
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-10-20T03:00:00Z"));
    var limiter = new org.grit.daynomy.exchangerate.service.ExchangeRateRequestLimiter(clock);
    for (int i = 0; i < 120; i++) limiter.acquire();
    assertThatThrownBy(limiter::acquire)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(ExchangeRateErrorCode.EXCHANGE_RATE_RATE_LIMITED));
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-10-20T03:01:00Z"));
    assertThatCode(limiter::acquire).doesNotThrowAnyException();
  }
}
