package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioAnalysisTaskServiceTest {

  @Mock private SavedPortfolioAnalysisTransactionService transactions;
  @Mock private SavedPortfolioAnalysisService analysis;
  @Mock private TaskExecutor executor;
  private SavedPortfolioAnalysisTaskService service;

  @BeforeEach
  void setUp() {
    service = new SavedPortfolioAnalysisTaskService(transactions, analysis, executor);
  }

  @Test
  void reusesExistingWorkWithoutSubmittingAnotherTask() {
    var processing = processing();
    given(transactions.prepareTask(1L, false))
        .willReturn(
            new SavedPortfolioAnalysisTransactionService.PreparedAnalysis(
                processing, null, List.of(), null));

    assertThat(service.start(1L, false)).isSameAs(processing);

    verifyNoInteractions(executor, analysis);
  }

  @Test
  void returnsProcessingBeforeRunningPreparedAnalysis() {
    var prepared = prepared();
    given(transactions.prepareTask(1L, false)).willReturn(prepared);
    given(transactions.get(1L)).willReturn(processing());

    assertThat(service.start(1L, false).status())
        .isEqualTo(SavedPortfolioAnalysisResponse.Status.PROCESSING);
    verifyNoInteractions(analysis);

    var task = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(task.capture());
    task.getValue().run();
    verify(analysis).execute(1L, prepared, false);
  }

  @Test
  void refreshUsesTheSameBackgroundExecutionAndRetainsFailureHandling() {
    var prepared = prepared();
    given(transactions.prepareTask(1L, true)).willReturn(prepared);
    given(transactions.get(1L)).willReturn(processing());
    given(analysis.execute(1L, prepared, true))
        .willThrow(new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_CHANGED));

    service.start(1L, true);
    var task = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(task.capture());

    assertThatCode(() -> task.getValue().run()).doesNotThrowAnyException();
    verify(analysis).execute(1L, prepared, true);
  }

  @Test
  void rejectedExecutionReleasesReservationAndAllowsRetry() {
    var prepared = prepared();
    given(transactions.prepareTask(1L, false)).willReturn(prepared);
    given(transactions.get(1L)).willReturn(processing());
    doThrow(new TaskRejectedException("executor full")).when(executor).execute(any());

    assertThatThrownBy(() -> service.start(1L, false))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY));

    verify(transactions).fail(1L, prepared.token(), "PORTFOLIO_ANALYSIS_BUSY");
    verifyNoInteractions(analysis);
  }

  private SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared() {
    return new SavedPortfolioAnalysisTransactionService.PreparedAnalysis(
        null, UUID.randomUUID(), List.of(), null);
  }

  private SavedPortfolioAnalysisResponse processing() {
    return new SavedPortfolioAnalysisResponse(
        SavedPortfolioAnalysisResponse.Status.PROCESSING,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null);
  }
}
