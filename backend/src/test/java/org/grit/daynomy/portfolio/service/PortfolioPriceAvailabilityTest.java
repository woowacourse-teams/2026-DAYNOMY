package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioPriceAvailabilityTest {
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private KrxCalendarClient krxCalendarClient;
  @InjectMocks private PortfolioPriceHistoryService service;

  @ParameterizedTest
  @CsvSource({
    "2026-10-20, 2026-10-21T13:59:59+09:00, 2026-10-19",
    "2026-10-20, 2026-10-21T14:00:00+09:00, 2026-10-20",
    "2026-10-18, 2026-10-19T13:59:59+09:00, 2026-10-15",
    "2026-10-18, 2026-10-19T14:00:00+09:00, 2026-10-18",
    "2026-10-16, 2026-10-17T15:00:00+09:00, 2026-10-15",
    "2026-10-11, 2026-10-12T13:59:59+09:00, 2026-10-07",
    "2026-10-11, 2026-10-12T14:00:00+09:00, 2026-10-11"
  })
  void onlyIncludesPricesPublishedByTheNextBusinessDayAtTwo(
      String to, String now, String expected) {
    given(krxCalendarClient.holidays(2026)).willReturn(Set.of(LocalDate.of(2026, 10, 9)));
    assertThat(service.availableDomesticThrough(LocalDate.parse(to), ZonedDateTime.parse(now)))
        .isEqualTo(LocalDate.parse(expected));
  }

  @Test
  void honorsAnExceptionalHolidayInTheStoredCalendar() {
    LocalDate holiday = LocalDate.of(2026, 10, 20);
    given(tradingDayRepository.findById(any()))
        .willAnswer(
            invocation ->
                holiday.equals(invocation.getArgument(0))
                    ? Optional.of(new MarketTradingDay(holiday, false))
                    : Optional.empty());
    given(krxCalendarClient.holidays(2026)).willReturn(Set.of());
    assertThat(
            service.availableDomesticThrough(
                holiday, ZonedDateTime.parse("2026-10-21T13:59:59+09:00")))
        .isEqualTo(LocalDate.of(2026, 10, 18));
  }
}
