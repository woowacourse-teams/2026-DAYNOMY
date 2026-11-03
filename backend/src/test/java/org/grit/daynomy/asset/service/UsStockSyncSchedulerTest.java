package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UsStockSyncSchedulerTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void enabledSchedulerCollectsMasterAndNamesBeforePrices(boolean enabled) {
    var service = mock(UsStockSyncService.class);
    var workflow = mock(UsStockSyncWorkflowService.class);
    LocalDate date = LocalDate.of(2026, 10, 16);
    when(workflow.synchronize())
        .thenReturn(
            new UsStockSyncResult(
                new StockSyncResult(date, 2, 2, 0, 0),
                new UsStockKoreanNameSyncResult(2, 2, 2, 0, 0),
                null));
    when(service.synchronizePrices(null)).thenReturn(new StockPriceSyncResult(date, 2, 2, 0, 0));
    new ApplicationContextRunner()
        .withUserConfiguration(UsStockSyncScheduler.class)
        .withBean(UsStockSyncService.class, () -> service)
        .withBean(UsStockSyncWorkflowService.class, () -> workflow)
        .withPropertyValues("stock.us.sync.enabled=" + enabled)
        .run(
            context -> {
              assertThat(context.containsBean("usStockSyncScheduler")).isEqualTo(enabled);
              if (enabled) {
                context.getBean(UsStockSyncScheduler.class).synchronize();
                var order = inOrder(workflow, service);
                order.verify(workflow).synchronize();
                order.verify(service).synchronizePrices(null);
              } else {
                verifyNoInteractions(service, workflow);
              }
            });
  }

  @org.junit.jupiter.api.Test
  void nameFailureStillCollectsPricesButMasterFailureStopsLaterStages() {
    var stocks = mock(UsStockSyncService.class);
    var names = mock(UsStockKoreanNameSyncService.class);
    var workflow = new UsStockSyncWorkflowService(stocks, names);
    var scheduler = new UsStockSyncScheduler(stocks, workflow);
    LocalDate date = LocalDate.of(2026, 10, 16);
    when(stocks.synchronizeMaster()).thenReturn(new StockSyncResult(date, 2, 2, 0, 0));
    when(names.synchronize())
        .thenThrow(
            new org.grit.daynomy.common.exception.BusinessException(
                org.grit.daynomy.asset.exception.AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_FAILED));
    when(stocks.synchronizePrices(null)).thenReturn(new StockPriceSyncResult(date, 2, 2, 0, 0));
    scheduler.synchronize();
    var order = inOrder(stocks, names);
    order.verify(stocks).synchronizeMaster();
    order.verify(names).synchronize();
    order.verify(stocks).synchronizePrices(null);
    clearInvocations(stocks, names);
    when(stocks.synchronizeMaster())
        .thenThrow(
            new org.grit.daynomy.common.exception.BusinessException(
                org.grit.daynomy.asset.exception.AssetErrorCode.US_STOCK_MASTER_DATA_NOT_FOUND));
    org.assertj.core.api.Assertions.assertThatThrownBy(scheduler::synchronize)
        .isInstanceOf(org.grit.daynomy.common.exception.BusinessException.class);
    verifyNoInteractions(names);
    verify(stocks, never()).synchronizePrices(any());
  }
}
