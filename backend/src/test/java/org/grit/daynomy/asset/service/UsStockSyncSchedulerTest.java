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
  void enabledSchedulerCollectsMasterBeforePrices(boolean enabled) {
    var service = mock(UsStockSyncService.class);
    LocalDate date = LocalDate.of(2026, 10, 16);
    when(service.synchronizeMaster()).thenReturn(new StockSyncResult(date, 2, 2, 0, 0));
    when(service.synchronizePrices(null)).thenReturn(new StockPriceSyncResult(date, 2, 2, 0, 0));
    new ApplicationContextRunner()
        .withUserConfiguration(UsStockSyncScheduler.class)
        .withBean(UsStockSyncService.class, () -> service)
        .withPropertyValues("stock.us.sync.enabled=" + enabled)
        .run(
            context -> {
              assertThat(context.containsBean("usStockSyncScheduler")).isEqualTo(enabled);
              if (enabled) {
                context.getBean(UsStockSyncScheduler.class).synchronize();
                var order = inOrder(service);
                order.verify(service).synchronizeMaster();
                order.verify(service).synchronizePrices(null);
              } else {
                verifyNoInteractions(service);
              }
            });
  }
}
