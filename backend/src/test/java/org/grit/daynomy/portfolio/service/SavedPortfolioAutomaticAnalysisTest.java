package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.grit.daynomy.portfolio.dto.*;
import org.junit.jupiter.api.Test;

class SavedPortfolioAutomaticAnalysisTest {
  final SavedPortfolioAnalysisTransactionService transactions =
      mock(SavedPortfolioAnalysisTransactionService.class);
  final PortfolioAnalysisService ai = mock(PortfolioAnalysisService.class);
  final SavedPortfolioAnalysisService service = new SavedPortfolioAnalysisService(transactions, ai);

  @Test
  void joinsManualWorkWithoutAnotherAIRequest() {
    var processing = mock(SavedPortfolioAnalysisResponse.class);
    when(transactions.prepareTask(1L, true))
        .thenReturn(
            new SavedPortfolioAnalysisTransactionService.PreparedAnalysis(
                processing, null, List.of(), null));
    assertThat(service.refreshAutomatically(1L)).isSameAs(processing);
    verifyNoInteractions(ai);
  }

  @Test
  void reusesExistingAnalysisComparisonAndStagesSavingBeforeSummary() {
    var token = UUID.randomUUID();
    var previous = mock(PortfolioAnalysisResponse.class);
    var candidate = mock(PortfolioAnalysisResponse.class);
    var prepared =
        new SavedPortfolioAnalysisTransactionService.PreparedAnalysis(
            null, token, List.of(), previous);
    when(transactions.prepareTask(1L, true)).thenReturn(prepared);
    when(ai.refreshAssets(any(), eq(previous)))
        .thenReturn(
            new PortfolioAnalysisRefresh.Revision(
                org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck.Outcome.UPDATED,
                candidate,
                "새 근거"));
    when(candidate.withOverallImpact("")).thenReturn(candidate);
    var core = mock(SavedPortfolioAnalysisResponse.class);
    when(core.summaryStatus()).thenReturn("PENDING");
    when(core.analysis()).thenReturn(candidate);
    when(core.analysisId()).thenReturn(101L);
    when(transactions.completeCore(1L, token, List.of(), candidate)).thenReturn(core);
    var summary = mock(PortfolioAnalysisResponse.class);
    when(ai.summarize(candidate)).thenReturn(summary);
    when(summary.overallImpact()).thenReturn("완성된 종합 설명");
    var completed = mock(SavedPortfolioAnalysisResponse.class);
    when(transactions.completeSummary(1L, token, 101L, "완성된 종합 설명")).thenReturn(completed);
    assertThat(service.refreshAutomatically(1L)).isSameAs(completed);
    var order = inOrder(transactions, ai);
    order.verify(transactions).completeCore(1L, token, List.of(), candidate);
    order.verify(ai).summarize(candidate);
    order.verify(transactions).completeSummary(1L, token, 101L, "완성된 종합 설명");
  }
}
