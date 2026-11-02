package org.grit.daynomy.league.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideQuestion;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideQuestions;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideResponse;
import org.grit.daynomy.league.service.InvestmentDecisionService;
import org.grit.daynomy.league.service.InvestorProfileService;
import org.grit.daynomy.league.service.LeagueReviewGuideService;
import org.grit.daynomy.league.service.LeagueReviewReminderService;
import org.grit.daynomy.league.service.LeagueService;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = MyLeagueController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class MyLeagueControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private InvestorProfileService profiles;
  @MockitoBean private InvestmentDecisionService transactions;
  @MockitoBean private LeagueService league;
  @MockitoBean private LeagueReviewGuideService reviewGuides;
  @MockitoBean private LeagueReviewReminderService reminders;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void reminderEndpointsUseAuthenticatedMemberAndNeverCache() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L, MemberRole.USER), null, java.util.List.of()));
    var key = java.util.UUID.randomUUID();
    var next = java.time.Instant.parse("2026-11-09T00:00:00Z");
    given(reminders.claim(1L, key))
        .willReturn(
            new org.grit.daynomy.league.dto.LeagueDto.ReviewReminderResponse(
                null, java.util.List.of(), next));
    given(reminders.snooze(1L, key))
        .willReturn(new org.grit.daynomy.league.dto.LeagueDto.ReviewReminderSnoozeResponse(next));
    for (String action : java.util.List.of("claim", "snooze")) {
      mvc.perform(
              post("/api/users/me/league/review-reminder/" + action)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"requestKey\":\"" + key + "\"}"))
          .andExpect(status().isOk())
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                  .string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.nextNotificationAt").value(next.toString()));
    }
    verify(reminders).claim(1L, key);
    verify(reminders).snooze(1L, key);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"requestKey\":\"invalid\"}"})
  void reminderRejectsMissingOrInvalidKey(String body) throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L, MemberRole.USER), null, java.util.List.of()));
    mvc.perform(
            post("/api/users/me/league/review-reminder/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(reminders);
  }

  @Test
  void returnsPrivateReviewGuideWithoutCaching() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L, MemberRole.USER), null, java.util.List.of()));
    given(reviewGuides.generate(1L, 7L))
        .willReturn(
            new ReviewGuideResponse(
                7L,
                "NO_EVIDENCE",
                java.util.List.of(),
                null,
                new ReviewGuideQuestions(
                    new ReviewGuideQuestion("무엇을 확인했나요?", java.util.List.of()),
                    new ReviewGuideQuestion("어떤 점이 달랐나요?", java.util.List.of()),
                    new ReviewGuideQuestion("다음에는 무엇을 확인하나요?", java.util.List.of()))));

    mvc.perform(post("/api/users/me/portfolio/decisions/7/review-guide"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(7))
        .andExpect(jsonPath("$.basis").isEmpty())
        .andExpect(jsonPath("$.questions.actualResult.text").value("무엇을 확인했나요?"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Cache-Control"))
                    .contains("no-store"));
    verify(reviewGuides).generate(1L, 7L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "101", "1.234"})
  void rejectsInvalidLossRateBeforeWriting(String rate) throws Exception {
    mvc.perform(
            post("/api/users/me/portfolio/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"requestKey":"decision-1","assetId":1,"decision":{"reason":"보유 이유",
        "expectedHoldingPeriod":"OVER_SIX_MONTHS","expectedChange":"성장","invalidationCondition":"악화",
        "maximumAcceptableLossRate":%s}}
        """
                        .formatted(rate)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"requestKey\":\"decision-1\",\"assetId\":1,\"decision\":null}",
        "{\"requestKey\":\"\",\"assetId\":0,\"decision\":{}}"
      })
  void rejectsMissingOrMalformedDecisionBeforeWriting(String body) throws Exception {
    mvc.perform(
            post("/api/users/me/portfolio/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}"})
  void rejectsBlankReasonBeforeWriting(String body) throws Exception {
    mvc.perform(
            put("/api/users/me/portfolio/decisions/7/reason")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }

  @Test
  void rejectsReasonLongerThan500CharactersBeforeWriting() throws Exception {
    mvc.perform(
            put("/api/users/me/portfolio/decisions/7/reason")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "가".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }
}
