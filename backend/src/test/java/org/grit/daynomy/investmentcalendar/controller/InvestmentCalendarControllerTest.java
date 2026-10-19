package org.grit.daynomy.investmentcalendar.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.service.InvestmentCalendarService;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = InvestmentCalendarController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class InvestmentCalendarControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean InvestmentCalendarService investmentCalendarService;

  @BeforeEach
  void setUpAuthentication() {
    AuthenticatedMember member = new AuthenticatedMember(3L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getsMonthlyCalendarForAuthenticatedMember() throws Exception {
    given(
            investmentCalendarService.get(
                3L, java.time.YearMonth.of(2026, 10), InvestmentCalendarScope.PORTFOLIO))
        .willReturn(new InvestmentCalendarResponse(2026, 10, List.of()));

    mockMvc
        .perform(
            get("/api/users/me/investment-calendar").param("year", "2026").param("month", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.month").value(10))
        .andExpect(jsonPath("$.events").isArray());

    then(investmentCalendarService)
        .should()
        .get(3L, java.time.YearMonth.of(2026, 10), InvestmentCalendarScope.PORTFOLIO);
  }

  @Test
  void rejectsInvalidMonth() throws Exception {
    mockMvc
        .perform(
            get("/api/users/me/investment-calendar").param("year", "2026").param("month", "13"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getsAllEventsWhenAllScopeIsRequested() throws Exception {
    given(
            investmentCalendarService.get(
                3L, java.time.YearMonth.of(2026, 10), InvestmentCalendarScope.ALL))
        .willReturn(new InvestmentCalendarResponse(2026, 10, List.of()));

    mockMvc
        .perform(
            get("/api/users/me/investment-calendar")
                .param("year", "2026")
                .param("month", "10")
                .param("scope", "ALL"))
        .andExpect(status().isOk());

    then(investmentCalendarService)
        .should()
        .get(3L, java.time.YearMonth.of(2026, 10), InvestmentCalendarScope.ALL);
  }
}
