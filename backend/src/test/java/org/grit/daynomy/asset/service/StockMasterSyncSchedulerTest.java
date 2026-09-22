package org.grit.daynomy.asset.service;

import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockMasterSyncSchedulerTest {

  @Mock private StockMasterSyncService stockMasterSyncService;
  @InjectMocks private StockMasterSyncScheduler scheduler;

  @Test
  @DisplayName("국내 주식 종목 동기화를 실행한다")
  void synchronize() {
    org.mockito.BDDMockito.given(stockMasterSyncService.synchronize())
        .willReturn(new StockSyncResult(LocalDate.of(2026, 9, 18), 2, 2, 0, 0));

    scheduler.synchronize();

    verify(stockMasterSyncService).synchronize();
  }
}
