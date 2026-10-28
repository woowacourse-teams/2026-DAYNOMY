package org.grit.daynomy.portfolio.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.portfolio.dto.GuestPortfolioAnalysisTaskResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = GuestPortfolioAnalysisTaskController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class GuestPortfolioAnalysisTaskControllerTest {
  private static final String API = "/api/portfolio/analysis/tasks";
  @Autowired private MockMvc mvc;
  @MockitoBean private GuestPortfolioAnalysisTaskService service;

  @Test
  void startingWithoutAnEstablishedSessionDoesNotScheduleWork() throws Exception {
    mvc.perform(
            post(API)
                .with(
                    request -> {
                      request.setRequestedSessionIdValid(false);
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assets\":[{\"assetName\":\"삼성전자\",\"weight\":100}]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PORTFOLIO_ANALYSIS_SESSION_REQUIRED"));
    verifyNoInteractions(service);
  }

  @Test
  void startingReturnsAcceptedAndAnOwnerScopedPollingLocation() throws Exception {
    UUID id = UUID.randomUUID();
    given(
            service.start(
                new PortfolioAnalysisRequest(
                    List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"))))))
        .willReturn(
            new GuestPortfolioAnalysisTaskResponse(id, "PROCESSING", null, null, null, false));

    mvc.perform(
            post(API)
                .with(
                    request -> {
                      request.setRequestedSessionIdValid(true);
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assets\":[{\"assetName\":\"삼성전자\",\"weight\":100}]}"))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", API + "/" + id))
        .andExpect(jsonPath("$.taskId").value(id.toString()));
  }

  @Test
  void invalidInputIsRejectedBeforeReservingTrial() throws Exception {
    mvc.perform(
            post(API)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assets\":[{\"assetName\":\"\",\"weight\":100}]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void anotherSessionsTaskUsesTheSameNotFoundResponseAsAnUnknownTask() throws Exception {
    UUID id = UUID.randomUUID();
    given(service.get(id))
        .willThrow(new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TASK_NOT_FOUND));

    mvc.perform(get(API + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PORTFOLIO_ANALYSIS_TASK_NOT_FOUND"));
  }
}
