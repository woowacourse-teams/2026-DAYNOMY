package org.grit.daynomy.portfolio.v2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.AsyncEvent;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.grit.daynomy.portfolio.v1.controller.SavedPortfolioAnalysisV1Controller;
import org.grit.daynomy.portfolio.v2.config.PortfolioAnalysisAsyncV2Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = {
      SavedPortfolioAnalysisV1Controller.class,
      SavedPortfolioAnalysisV2Controller.class
    },
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, PortfolioAnalysisAsyncV2Config.class})
@EnableWebSecurity
class SavedPortfolioAnalysisV2ControllerTest {
  @Autowired MockMvc mvc;

  @MockitoBean(name = "savedPortfolioAnalysisV1")
  SavedPortfolioAnalysisService v1;

  @MockitoBean(name = "savedPortfolioAnalysisV2")
  SavedPortfolioAnalysisService v2;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  private void authenticate() {
    var member = new AuthenticatedMember(1L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(member, null, java.util.List.of()));
  }

  @Test
  void pendingAnalysesReturnRequestThreadAndRunOnVirtualThreads() throws Exception {
    authenticate();
    MDC.put("requestId", "async-test-request");
    int count = 210;
    var started = new CountDownLatch(count);
    var release = new CountDownLatch(1);
    when(v2.analyze(1L, null))
        .thenAnswer(
            invocation -> {
              assertThat(Thread.currentThread().isVirtual()).isTrue();
              assertThat(MDC.get("requestId")).isEqualTo("async-test-request");
              started.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("test wait expired");
              return SavedPortfolioAnalysisResponse.empty();
            });
    var requests = new ArrayList<MvcResult>();
    try {
      for (int i = 0; i < count; i++) {
        requests.add(
            mvc.perform(post("/api/v2/users/me/portfolio/analysis"))
                .andExpect(request().asyncStarted())
                .andReturn());
      }
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(release.getCount()).isEqualTo(1);
    } finally {
      release.countDown();
    }
    for (var result : requests) {
      result.getAsyncResult(5000);
      mvc.perform(asyncDispatch(result))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("EMPTY"));
    }
  }

  @Test
  void asyncFailureUsesExistingErrorResponse() throws Exception {
    authenticate();
    when(v2.analyze(1L, null))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));
    var result =
        mvc.perform(post("/api/v2/users/me/portfolio/analysis"))
            .andExpect(request().asyncStarted())
            .andReturn();
    result.getAsyncResult(5000);
    mvc.perform(asyncDispatch(result)).andExpect(status().isBadGateway());
  }

  @Test
  void asyncTimeoutDoesNotReturnSuccessfulAnalysis() throws Exception {
    authenticate();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(v2.analyze(1L, null))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              // 작업 종료와 타임아웃 콜백의 경쟁 없이 HTTP 타임아웃 응답을 검증한다.
              boolean interrupted = false;
              while (release.getCount() > 0) {
                try {
                  if (!release.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("test wait expired");
                } catch (InterruptedException exception) {
                  interrupted = true;
                }
              }
              if (interrupted) Thread.currentThread().interrupt();
              return SavedPortfolioAnalysisResponse.empty();
            });
    try {
      var result =
          mvc.perform(post("/api/v2/users/me/portfolio/analysis"))
              .andExpect(request().asyncStarted())
              .andReturn();
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      var context = (MockAsyncContext) result.getRequest().getAsyncContext();
      assertThat(context.getTimeout()).isEqualTo(300_000L);
      for (var listener : context.getListeners()) listener.onTimeout(new AsyncEvent(context));
      // MockMvc의 작업 완료 콜백은 타임아웃 경로에서는 실행되지 않는다.
      var timeoutResult =
          org.springframework.web.context.request.async.WebAsyncUtils.getAsyncManager(
                  result.getRequest())
              .getConcurrentResult();
      assertThat(timeoutResult).isInstanceOf(BusinessException.class);
      assertThat(((BusinessException) timeoutResult).errorCode())
          .isEqualTo(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } finally {
      release.countDown();
    }
  }

  @Test
  void v1StillRespondsSynchronously() throws Exception {
    authenticate();
    when(v1.analyze(1L, null)).thenReturn(SavedPortfolioAnalysisResponse.empty());
    mvc.perform(post("/api/v1/users/me/portfolio/analysis"))
        .andExpect(request().asyncNotStarted())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EMPTY"));
  }
}
