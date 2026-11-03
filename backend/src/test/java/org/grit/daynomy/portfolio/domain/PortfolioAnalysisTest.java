package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisTest {

  private static final Instant ANALYZED_AT = Instant.parse("2026-10-12T07:00:00Z");

  @Test
  void summaryCompletionPreservesTheOriginalAnalysisSnapshotAndTime() {
    var analysis = initial();
    var snapshot = analysis.getSnapshot();
    analysis.awaitSummary();

    analysis.completeSummary("확보한 근거에 따라 종합 설명을 작성했어요.");

    assertThat(analysis.getSummaryStatus()).isEqualTo(PortfolioAnalysis.SummaryStatus.READY);
    assertThat(analysis.getOverallImpact()).isEqualTo("확보한 근거에 따라 종합 설명을 작성했어요.");
    assertThat(analysis.getSnapshot()).isSameAs(snapshot);
    assertThat(analysis.getAnalyzedAt()).isEqualTo(ANALYZED_AT);
    assertThatIllegalStateException().isThrownBy(() -> analysis.completeSummary("다른 설명이에요."));
  }

  @Test
  void failedSummaryDoesNotDiscardTheCoreAnalysis() {
    var analysis = initial();
    var snapshot = analysis.getSnapshot();
    analysis.awaitSummary();

    analysis.failSummary();

    assertThat(analysis.getSummaryStatus()).isEqualTo(PortfolioAnalysis.SummaryStatus.FAILED);
    assertThat(analysis.getSnapshot()).isSameAs(snapshot);
    assertThat(analysis.getOverallImpact()).isEmpty();
    assertThatIllegalStateException().isThrownBy(analysis::awaitSummary);
  }

  private PortfolioAnalysis initial() {
    return PortfolioAnalysis.create(
        mock(Portfolio.class),
        null,
        "portfolio-v1",
        "test-model",
        new PortfolioAnalysisSnapshot(
            List.of(), PortfolioImpactSummary.calculate(List.of()), "", List.of()),
        ANALYZED_AT,
        "최초 분석");
  }

  @Test
  @DisplayName("다른 포트폴리오의 분석을 이전 분석으로 연결할 수 없다")
  void rejectsAnotherPortfolioAnalysis() {
    Portfolio portfolio = portfolio(1L);
    PortfolioAnalysis previous = previous(portfolio(2L));

    assertThatIllegalArgumentException().isThrownBy(() -> revise(portfolio, previous, ANALYZED_AT));
  }

  @Test
  @DisplayName("이전 분석보다 과거 시각으로 새 분석을 만들 수 없다")
  void rejectsBackdatedRevision() {
    Portfolio portfolio = portfolio(1L);
    PortfolioAnalysis previous = previous(portfolio);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> revise(portfolio, previous, ANALYZED_AT.minusSeconds(1)));
  }

  private Portfolio portfolio(Long id) {
    Portfolio portfolio = mock(Portfolio.class);
    when(portfolio.getId()).thenReturn(id);
    return portfolio;
  }

  private PortfolioAnalysis previous(Portfolio portfolio) {
    PortfolioAnalysis previous = mock(PortfolioAnalysis.class);
    when(previous.getId()).thenReturn(10L);
    when(previous.getPortfolio()).thenReturn(portfolio);
    when(previous.getAnalyzedAt()).thenReturn(ANALYZED_AT);
    return previous;
  }

  private void revise(Portfolio portfolio, PortfolioAnalysis previous, Instant analyzedAt) {
    PortfolioAnalysis.create(
        portfolio,
        previous,
        "portfolio-v1",
        "test-model",
        new PortfolioAnalysisSnapshot(
            List.of(), PortfolioImpactSummary.calculate(List.of()), "", List.of()),
        analyzedAt,
        "정정 반영");
  }
}
