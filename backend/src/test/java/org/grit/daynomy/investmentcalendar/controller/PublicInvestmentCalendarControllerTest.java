package org.grit.daynomy.investmentcalendar.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.service.InvestmentCalendarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = PublicInvestmentCalendarController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class PublicInvestmentCalendarControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean InvestmentCalendarService investmentCalendarService;

  @Test
  void getsGuestCalendarWithoutAuthentication() throws Exception {
    given(
            investmentCalendarService.getForGuest(
                YearMonth.of(2026, 10), InvestmentCalendarScope.PORTFOLIO, Map.of()))
        .willReturn(new InvestmentCalendarResponse(2026, 10, List.of()));

    mockMvc
        .perform(
            post("/api/investment-calendar")
                .param("year", "2026")
                .param("month", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdings\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events").isArray());

    then(investmentCalendarService)
        .should()
        .getForGuest(YearMonth.of(2026, 10), InvestmentCalendarScope.PORTFOLIO, Map.of());
  }

  @Test
  void rejectsMoreThanFiftyGuestHoldings() throws Exception {
    String holdings =
        java.util.stream.LongStream.rangeClosed(1, 51)
            .mapToObj(id -> "{\"assetId\":" + id + ",\"quantity\":1}")
            .collect(java.util.stream.Collectors.joining(","));

    mockMvc
        .perform(
            post("/api/investment-calendar")
                .param("year", "2026")
                .param("month", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdings\":[" + holdings + "]}"))
        .andExpect(status().isBadRequest());

    then(investmentCalendarService).shouldHaveNoInteractions();
  }
}
