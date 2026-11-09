package org.grit.daynomy.exchangerate.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.exchangerate.ExchangeRateErrorCode;
import org.grit.daynomy.exchangerate.dto.ExchangeRateResponse;
import org.grit.daynomy.exchangerate.service.ExchangeRateRequestLimiter;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = ExchangeRateController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ExchangeRateControllerTest {
  private static final String PATH = "/api/exchange-rates/usd-krw";

  @Autowired private MockMvc mvc;
  @MockitoBean private ExchangeRateService service;
  @MockitoBean private ExchangeRateRequestLimiter limiter;

  @Test
  void returnsQuoteWithRequestedAndBaseDates() throws Exception {
    LocalDate requestedDate = LocalDate.of(2026, 10, 25);
    LocalDate baseDate = LocalDate.of(2026, 10, 23);
    given(service.quote(requestedDate))
        .willReturn(
            new ExchangeRateResponse(
                requestedDate, baseDate, new BigDecimal("1420.25"), "USD", "KRW"));

    mvc.perform(get(PATH).param("date", "2026-10-25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestedDate").value("2026-10-25"))
        .andExpect(jsonPath("$.baseDate").value("2026-10-23"))
        .andExpect(jsonPath("$.rate").value(1420.25))
        .andExpect(jsonPath("$.baseCurrency").value("USD"))
        .andExpect(jsonPath("$.quoteCurrency").value("KRW"));
  }

  @Test
  void rejectsMissingDate() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isBadRequest());

    then(limiter).shouldHaveNoInteractions();
    then(service).shouldHaveNoInteractions();
  }

  @Test
  void rejectsMalformedDate() throws Exception {
    mvc.perform(get(PATH).param("date", "2026/10/25"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("date"));

    then(limiter).shouldHaveNoInteractions();
    then(service).shouldHaveNoInteractions();
  }

  @Test
  void returnsTooManyRequestsBeforeFetchingQuote() throws Exception {
    willThrow(new BusinessException(ExchangeRateErrorCode.EXCHANGE_RATE_RATE_LIMITED))
        .given(limiter)
        .acquire();

    mvc.perform(get(PATH).param("date", "2026-10-25"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("EXCHANGE_RATE_RATE_LIMITED"));

    then(service).shouldHaveNoInteractions();
  }
}
