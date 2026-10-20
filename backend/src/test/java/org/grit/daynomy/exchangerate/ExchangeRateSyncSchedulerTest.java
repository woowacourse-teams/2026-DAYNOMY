package org.grit.daynomy.exchangerate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.grit.daynomy.exchangerate.dto.ExchangeRateResponse;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.exchangerate.service.ExchangeRateSyncScheduler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExchangeRateSyncSchedulerTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void onlyEnabledSchedulerRefreshesAndCanRunAgainAfterFailure(boolean enabled) {
    var service = mock(ExchangeRateService.class);
    var date = LocalDate.of(2026, 10, 16);
    when(service.refreshToday())
        .thenThrow(new IllegalStateException("unavailable"))
        .thenReturn(ExchangeRateResponse.from(new UsdKrwRate(date, date, BigDecimal.ONE)));
    new ApplicationContextRunner()
        .withUserConfiguration(ExchangeRateSyncScheduler.class)
        .withBean(ExchangeRateService.class, () -> service)
        .withPropertyValues("exchange-rate.sync.enabled=" + enabled)
        .run(
            context -> {
              assertThat(context.containsBean("exchangeRateSyncScheduler")).isEqualTo(enabled);
              if (enabled) {
                var scheduler = context.getBean(ExchangeRateSyncScheduler.class);
                assertThatCode(scheduler::synchronize).doesNotThrowAnyException();
                scheduler.synchronize();
                verify(service, times(2)).refreshToday();
              } else {
                verifyNoInteractions(service);
              }
            });
  }
}
