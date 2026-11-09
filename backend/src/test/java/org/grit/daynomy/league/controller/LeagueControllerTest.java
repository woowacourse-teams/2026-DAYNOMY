package org.grit.daynomy.league.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.dto.LeagueDto.*;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.service.LeagueService;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = LeagueController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@EnableWebSecurity
class LeagueControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private LeagueService service;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rankingsUseAnonymousDefaultsAndNeverCache() throws Exception {
    var week = LocalDate.of(2026, 11, 9);
    given(service.rankings(null, null, LeagueType.WEEKLY_RETURN, 0, 20))
        .willReturn(
            new RankingResponse(
                week,
                week.plusDays(6),
                LeagueType.WEEKLY_RETURN,
                false,
                0,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null));
    mvc.perform(get("/api/league/rankings"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.weekStart").value("2026-11-09"))
        .andExpect(jsonPath("$.leagueType").value("WEEKLY_RETURN"))
        .andExpect(jsonPath("$.rankings").isEmpty())
        .andExpect(jsonPath("$.totalCount").value(0));
    then(service).should().rankings(null, null, LeagueType.WEEKLY_RETURN, 0, 20);
  }

  @Test
  void rankingsConvertDatesAndPageAndPassTheAuthenticatedMember() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(7L, MemberRole.USER), null, List.of()));
    mvc.perform(
            get("/api/league/rankings")
                .param("weekStart", "2026-11-02")
                .param("leagueType", "WEEKLY_RETURN")
                .param("page", "2")
                .param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    then(service)
        .should()
        .rankings(7L, LocalDate.of(2026, 11, 2), LeagueType.WEEKLY_RETURN, 2, 100);
  }

  @ParameterizedTest
  @CsvSource({
    "page,-1",
    "page,first",
    "size,0",
    "size,101",
    "size,large",
    "leagueType,UNKNOWN",
    "weekStart,invalid-date"
  })
  void rejectsInvalidRankingParametersBeforeCallingService(String field, String value)
      throws Exception {
    mvc.perform(get("/api/league/rankings").param(field, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
        .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    verifyNoInteractions(service);
  }

  @Test
  void serializesWeeksAndPublicDetails() throws Exception {
    var week = LocalDate.of(2026, 11, 9);
    given(service.weeks())
        .willReturn(
            new WeeksResponse(List.of(new WeekResponse(week, week.plusDays(6), false, null))));
    given(service.investorDetail("investor"))
        .willReturn(new InvestorDetailResponse("investor", week, List.of(), List.of()));
    mvc.perform(get("/api/league/weeks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-11-09"));
    mvc.perform(get("/api/league/investors/investor/details"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.publicId").value("investor"))
        .andExpect(jsonPath("$.holdings").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void investorPassesOptionalViewer(boolean authenticated) throws Exception {
    if (authenticated) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  new AuthenticatedMember(7L, MemberRole.USER), null, List.of()));
    }
    mvc.perform(get("/api/league/investors/investor"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    then(service).should().publicInvestor(authenticated ? 7L : null, "investor");
  }

  @Test
  void dailyHistoryConvertsOptionalWeekAndRejectsMalformedDates() throws Exception {
    mvc.perform(get("/api/league/investors/investor/daily-history"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            get("/api/league/investors/investor/daily-history").param("weekStart", "2026-11-02"))
        .andExpect(status().isOk());
    then(service).should().dailyHistory("investor", null);
    then(service).should().dailyHistory("investor", LocalDate.of(2026, 11, 2));
    mvc.perform(get("/api/league/investors/investor/daily-history").param("weekStart", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    then(service).shouldHaveNoMoreInteractions();
  }

  @Test
  void preservesBusinessErrorStatusAndMessage() throws Exception {
    given(service.investorDetail("private"))
        .willThrow(new BusinessException(LeagueErrorCode.DETAIL_NOT_PUBLIC));
    mvc.perform(get("/api/league/investors/private/details"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DETAIL_NOT_PUBLIC"))
        .andExpect(jsonPath("$.message").value(LeagueErrorCode.DETAIL_NOT_PUBLIC.message()));
  }
}
