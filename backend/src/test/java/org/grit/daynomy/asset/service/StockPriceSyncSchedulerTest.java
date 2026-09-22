package org.grit.daynomy.asset.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockPriceSyncSchedulerTest {

  @Mock private StockPriceSyncService stockPriceSyncService;
  @InjectMocks private StockPriceSyncScheduler scheduler;

  @Test
  @DisplayName("국내 주식 종가 동기화를 실행한다")
  void synchronize() {
    given(stockPriceSyncService.synchronize())
        .willReturn(new StockPriceSyncResult(LocalDate.of(2026, 9, 18), 2, 2, 0, 0));

    scheduler.synchronize();

    verify(stockPriceSyncService).synchronize();
  }
}
