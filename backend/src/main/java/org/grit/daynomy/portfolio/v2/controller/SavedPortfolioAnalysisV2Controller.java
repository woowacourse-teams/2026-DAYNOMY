package org.grit.daynomy.portfolio.v2.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@Slf4j
@RestController
@RequestMapping("/api/v2/users/me/portfolio/analysis")
public class SavedPortfolioAnalysisV2Controller {
  private final SavedPortfolioAnalysisService service;
  private final AsyncTaskExecutor executor;

  public SavedPortfolioAnalysisV2Controller(
      @Qualifier("savedPortfolioAnalysisV2") SavedPortfolioAnalysisService service,
      @Qualifier("portfolioAnalysisExecutorV2") AsyncTaskExecutor executor) {
    this.service = service;
    this.executor = executor;
  }

  @Operation(summary = "포트폴리오 분석 V2: 가격 일괄 조회 및 종목 AI 결과 재사용")
  @PostMapping
  public WebAsyncTask<SavedPortfolioAnalysisResponse> analyze(
      @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest request,
      HttpServletResponse response) {
    Long memberId = member.memberId();
    long receivedAt = System.nanoTime();
    String requestId = MDC.get("requestId");
    // ASYNC 재디스패치에서도 기존 보안 필터가 같은 요청의 인증을 확인할 수 있도록 보존한다.
    new RequestAttributeSecurityContextRepository()
        .saveContext(SecurityContextHolder.getContext(), request, response);
    // 분석 대기 중에는 Servlet 요청 스레드를 반환한다. HTTP 제한 시간은 기존과 같다.
    var task =
        new WebAsyncTask<>(
            300_000L,
            executor,
            () -> {
              log.atInfo()
                  .addKeyValue("event", "portfolio.analysis.v2.started")
                  .addKeyValue("requestId", requestId)
                  .addKeyValue("waitMs", elapsedMillis(receivedAt))
                  .log("V2 분석 작업 시작");
              try {
                var result = service.analyze(memberId, null);
                log.atInfo()
                    .addKeyValue("event", "portfolio.analysis.v2.completed")
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("durationMs", elapsedMillis(receivedAt))
                    .addKeyValue("status", result.status())
                    .log("V2 분석 작업 완료");
                return result;
              } catch (RuntimeException exception) {
                log.atWarn()
                    .addKeyValue("event", "portfolio.analysis.v2.failed")
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("durationMs", elapsedMillis(receivedAt))
                    .addKeyValue("errorType", exception.getClass().getSimpleName())
                    .log("V2 분석 작업 실패");
                throw exception;
              }
            });
    task.onTimeout(
        () -> {
          log.atWarn()
              .addKeyValue("event", "portfolio.analysis.v2.timeout")
              .addKeyValue("requestId", requestId)
              .addKeyValue("durationMs", elapsedMillis(receivedAt))
              .log("V2 분석 응답 대기 시간 초과");
          throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
        });
    return task;
  }

  private static long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
