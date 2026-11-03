package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.Test;

class GuestPortfolioAnalysisTrialStateTest {
  private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

  @Test
  void reservesTrialAtStartAndReusesEquivalentInputEvenAfterFailure() {
    var state = new GuestPortfolioAnalysisTrialState();
    var token = state.reserve(assets("Samsung", "100.0"), NOW);
    var id = state.getTaskId();

    assertThat(state.reserve(assets("samsung", "100"), NOW)).isNull();
    assertThatThrownBy(() -> state.reserve(assets("다른 종목", "100"), NOW))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(state::reserveLegacy).isInstanceOf(BusinessException.class);
    state.fail(token, "AI_PORTFOLIO_ANALYSIS_FAILED", NOW);

    assertThat(state.reserve(assets("Samsung", "100"), NOW)).isNull();
    assertThat(state.getTaskId()).isEqualTo(id);
    assertThat(state.retryAllowed()).isTrue();
  }

  @Test
  void confirmedCoreFailureAllowsExactlyOneExplicitRetry() {
    var state = new GuestPortfolioAnalysisTrialState();
    var token = state.reserve(assets("삼성전자", "100"), NOW);
    state.fail(token, "AI_PORTFOLIO_ANALYSIS_FAILED", NOW);
    var retry = state.retry(state.getTaskId(), NOW);

    assertThat(state.retry(state.getTaskId(), NOW)).isNull();
    assertThat(state.publishCore(token, NOW)).isFalse();
    state.fail(retry, "AI_PORTFOLIO_ANALYSIS_FAILED", NOW);

    assertThat(state.retryAllowed()).isFalse();
    assertThatThrownBy(() -> state.retry(state.getTaskId(), NOW))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void executorRejectionDoesNotConsumeAnAnalysisAttempt() {
    var state = new GuestPortfolioAnalysisTrialState();
    var rejected = state.reserve(assets("삼성전자", "100"), NOW);
    state.reject(rejected, NOW);
    var first = state.retry(state.getTaskId(), NOW);
    state.fail(first, "AI_PORTFOLIO_ANALYSIS_FAILED", NOW);

    assertThat(state.retryAllowed()).isTrue();
    assertThat(state.retry(state.getTaskId(), NOW)).isNotNull();
  }

  @Test
  void failedSummaryPreservesReadyCoreAndDoesNotAllowAnotherAnalysis() {
    var state = new GuestPortfolioAnalysisTrialState();
    var token = state.reserve(assets("삼성전자", "100"), NOW);
    assertThat(state.complete(token, NOW)).isFalse();
    state.publishCore(token, NOW);
    assertThat(state.publishCore(token, NOW)).isFalse();
    state.fail(token, "AI_PORTFOLIO_ANALYSIS_FAILED", NOW);

    assertThat(state.getStatus()).isEqualTo(GuestPortfolioAnalysisTrialState.Status.READY);
    assertThat(state.getSummaryStatus()).isEqualTo(PortfolioAnalysis.SummaryStatus.FAILED);
    assertThat(state.retryAllowed()).isFalse();
    assertThat(state.complete(token, NOW)).isFalse();
  }

  @Test
  void timeoutDoesNotPermitRetryWhileOldProviderWorkMightStillBeRunning() {
    var state = new GuestPortfolioAnalysisTrialState();
    var token = state.reserve(assets("삼성전자", "100"), NOW);
    state.expire(NOW.plusSeconds(300));

    assertThat(state.getStatus()).isEqualTo(GuestPortfolioAnalysisTrialState.Status.FAILED);
    assertThat(state.getFailureCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED.code());
    assertThat(state.retryAllowed()).isFalse();
    assertThat(state.publishCore(token, NOW.plusSeconds(301))).isFalse();
  }

  @Test
  void taskIdIsRequiredForQueryAndRetryAndLegacyTrialUsesTheSameEntitlement() {
    var state = new GuestPortfolioAnalysisTrialState();
    assertThatThrownBy(() -> state.requireTask(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class);
    state.reserveLegacy();
    assertThatThrownBy(() -> state.reserve(assets("삼성전자", "100"), NOW))
        .isInstanceOf(BusinessException.class);
    state.releaseLegacy();
    assertThat(state.reserve(assets("삼성전자", "100"), NOW)).isNotNull();
  }

  private List<PortfolioAnalysisSnapshot.Asset> assets(String name, String weight) {
    return List.of(new PortfolioAnalysisSnapshot.Asset(name, new BigDecimal(weight)));
  }
}
