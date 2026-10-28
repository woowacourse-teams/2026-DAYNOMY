package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class GuestPortfolioAnalysisTaskServiceTest {
  @Mock private PortfolioAnalysisService analysis;
  private GuestPortfolioAnalysisTrial trial;
  private GuestPortfolioAnalysisTaskService service;
  private final List<Runnable> queued = new ArrayList<>();
  private final PortfolioAnalysisRequest input =
      new PortfolioAnalysisRequest(
          List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"))));

  @BeforeEach
  void setUp() {
    trial = new GuestPortfolioAnalysisTrial();
    service = new GuestPortfolioAnalysisTaskService(trial, analysis, queued::add);
  }

  @Test
  void resubmissionAndQueryNeverScheduleAdditionalWork() {
    var first = service.start(input);
    assertThat(first.status()).isEqualTo("PROCESSING");
    assertThat(service.start(input).taskId()).isEqualTo(first.taskId());
    assertThat(service.get(first.taskId()).status()).isEqualTo("PROCESSING");
    assertThat(queued).hasSize(1);
    var core = PortfolioAnalysisResponse.empty();
    given(analysis.analyzeAssets(input)).willReturn(core);
    given(analysis.summarize(core))
        .willAnswer(
            invocation -> {
              assertThat(service.get().analysis()).isSameAs(core);
              assertThat(service.get().summaryStatus()).isEqualTo("PENDING");
              return core.withOverallImpact("종합 설명을 완성했어요.");
            });

    queued.getFirst().run();

    assertThat(service.get().status()).isEqualTo("READY");
    assertThat(service.start(input).taskId()).isEqualTo(first.taskId());
    assertThat(queued).hasSize(1);
    verify(analysis).analyzeAssets(input);
  }

  @Test
  void confirmedFailureOnlyRunsAgainOnExplicitRetry() {
    given(analysis.analyzeAssets(input))
        .willThrow(new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE));
    var first = service.start(input);
    queued.getFirst().run();
    assertThat(service.start(input).status()).isEqualTo("FAILED");
    assertThat(queued).hasSize(1);

    service.retry(first.taskId());
    queued.getLast().run();

    assertThat(service.get().retryAllowed()).isFalse();
    assertThatThrownBy(() -> service.retry(first.taskId())).isInstanceOf(BusinessException.class);
    verify(analysis, times(2)).analyzeAssets(input);
  }

  @Test
  void summaryFailureRetainsCoreAndDoesNotPermitRetry() {
    var core = PortfolioAnalysisResponse.empty();
    given(analysis.analyzeAssets(input)).willReturn(core);
    given(analysis.summarize(core)).willThrow(new IllegalStateException("provider failure"));
    var first = service.start(input);
    queued.getFirst().run();

    assertThat(service.get().status()).isEqualTo("READY");
    assertThat(service.get().summaryStatus()).isEqualTo("FAILED");
    assertThat(service.get().analysis()).isSameAs(core);
    assertThatThrownBy(() -> service.retry(first.taskId())).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectedSubmissionCanBeRetriedWithoutConsumingAnotherAnalysisAttempt() {
    var rejecting =
        new GuestPortfolioAnalysisTaskService(
            trial,
            analysis,
            task -> {
              throw new TaskRejectedException("full");
            });
    assertThatThrownBy(() -> rejecting.start(input))
        .isInstanceOfSatisfying(
            BusinessException.class,
            failure ->
                assertThat(failure.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY));

    assertThat(service.get().retryAllowed()).isTrue();
    service.retry(service.get().taskId());
    assertThat(queued).hasSize(1);
  }

  @Test
  void emptyInputDoesNotReserveTrialAndInvalidInputDoesNotScheduleWork() {
    assertThat(service.start(new PortfolioAnalysisRequest(List.of())).taskId()).isNull();
    assertThat(service.get().status()).isEqualTo("EMPTY");
    org.mockito.Mockito.doThrow(
            new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_WEIGHT_TOTAL))
        .when(analysis)
        .validate(any());
    assertThatThrownBy(() -> service.start(input)).isInstanceOf(BusinessException.class);
    assertThat(queued).isEmpty();
    assertThat(service.get().status()).isEqualTo("EMPTY");
  }
}
