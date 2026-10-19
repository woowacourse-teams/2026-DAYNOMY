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
    var service = new ExchangeRateService(repository, client);
    var date = LocalDate.of(2020, 10, 18);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
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
    var service = new ExchangeRateService(repository, client);
    when(client.getUsdKrwRates(any(), any()))
        .thenReturn(List.of(row("2020-10-16", "0"), row("2020-10-19", "1400")));
    assertThatThrownBy(() -> service.quote(LocalDate.of(2020, 10, 18)))
        .isInstanceOf(BusinessException.class);
    verify(repository, never()).save(any());
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
    var service = new ExchangeRateService(repository, client);
    var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    var cutoff = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);
    var cached = new UsdKrwRate(today, cutoff.minusDays(1), new BigDecimal("1300"));
    when(repository.findById(today)).thenReturn(Optional.of(cached));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
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
    verify(repository, never()).save(any());
    assertThat(cached.getRate()).isEqualByComparingTo("1300");
  }
}
