package org.grit.daynomy.portfolio.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = SavedPortfolioAnalysisController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class SavedPortfolioAnalysisTaskControllerTest {

  private static final String API = "/api/users/me/portfolio/analysis";

  @Autowired private MockMvc mvc;
  @MockitoBean private SavedPortfolioAnalysisService analysis;
  @MockitoBean private SavedPortfolioAnalysisTaskService tasks;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({"/tasks,false", "/tasks/refresh,true"})
  void acceptsBackgroundWorkAndProvidesPollingLocation(String path, boolean refresh)
      throws Exception {
    authenticate();
    given(tasks.start(1L, refresh))
        .willReturn(
            new SavedPortfolioAnalysisResponse(
                SavedPortfolioAnalysisResponse.Status.PROCESSING,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null));

    mvc.perform(post(API + path))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", API))
        .andExpect(jsonPath("$.status").value("PROCESSING"));
  }

  @Test
  void returnsExistingResultWithoutMarkingItAsAcceptedWork() throws Exception {
    authenticate();
    given(tasks.start(1L, false)).willReturn(SavedPortfolioAnalysisResponse.empty());

    mvc.perform(post(API + "/tasks"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void saturationReturnsRetryableServiceUnavailable() throws Exception {
    authenticate();
    given(tasks.start(1L, false))
        .willThrow(new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY));

    mvc.perform(post(API + "/tasks"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("PORTFOLIO_ANALYSIS_BUSY"));
  }

  @Test
  void exhaustedManualRefreshesReturnTooManyRequests() throws Exception {
    authenticate();
    given(tasks.start(1L, true))
        .willThrow(
            new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REFRESH_LIMIT_EXCEEDED));
    mvc.perform(post(API + "/tasks/refresh"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("PORTFOLIO_ANALYSIS_REFRESH_LIMIT_EXCEEDED"));
  }

  private void authenticate() {
    var member = new AuthenticatedMember(1L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }
}
