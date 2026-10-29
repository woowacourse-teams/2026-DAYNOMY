package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UsStockSyncWorkflowServiceTest {
  private final UsStockSyncService stocks = mock(UsStockSyncService.class);
  private final UsStockKoreanNameSyncService names = mock(UsStockKoreanNameSyncService.class);
  private final UsStockSyncWorkflowService workflow = new UsStockSyncWorkflowService(stocks, names);
  private final StockSyncResult master =
      new StockSyncResult(LocalDate.of(2026, 10, 27), 2, 2, 0, 0);

  @Test
  void masterCompletesBeforeNamesAndCombinedExecutionCannotOverlap() {
    when(stocks.synchronizeMaster()).thenReturn(master);
    when(names.synchronize())
        .thenAnswer(
            call -> {
              assertThatThrownBy(workflow::synchronize)
                  .isInstanceOfSatisfying(
                      BusinessException.class,
                      failure ->
                          assertThat(failure.errorCode())
                              .isEqualTo(AssetErrorCode.US_STOCK_SYNC_ALREADY_RUNNING));
              return new UsStockKoreanNameSyncResult(2, 2, 2, 0, 0);
            });
    assertThat(workflow.synchronize().koreanNameErrorCode()).isNull();
    var order = inOrder(stocks, names);
    order.verify(stocks).synchronizeMaster();
    order.verify(names).synchronize();
  }

  @Test
  void masterFailureSkipsNamesAndAllowsNextExecution() {
    when(stocks.synchronizeMaster())
        .thenThrow(new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED))
        .thenReturn(master);
    when(names.synchronize()).thenReturn(new UsStockKoreanNameSyncResult(2, 2, 2, 0, 0));
    assertThatThrownBy(workflow::synchronize).isInstanceOf(BusinessException.class);
    verifyNoInteractions(names);
    assertThat(workflow.synchronize().master()).isEqualTo(master);
  }

  @Test
  void nameFailureReturnsCommittedMasterAndSafeFailureCode() {
    when(stocks.synchronizeMaster()).thenReturn(master);
    when(names.synchronize())
        .thenThrow(new BusinessException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED));
    assertThat(workflow.synchronize())
        .isEqualTo(new UsStockSyncResult(master, null, "KIS_STOCK_MASTER_REQUEST_FAILED"));
    doThrow(new DataIntegrityViolationException("private database detail"))
        .when(names)
        .synchronize();
    assertThat(workflow.synchronize())
        .isEqualTo(new UsStockSyncResult(master, null, "US_STOCK_KOREAN_NAME_SYNC_FAILED"));
  }
}
