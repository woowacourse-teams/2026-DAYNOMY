package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class StockPriceSyncMetricsTest {

  @Test
  void olderBackfillDoesNotMoveLatestBaseDateBackward() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    StockPriceSyncMetrics metrics = new StockPriceSyncMetrics(registry);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    metrics.recordSuccess(new StockPriceSyncResult(today, 10, 10, 0, 0));

    metrics.recordSuccess(new StockPriceSyncResult(today.minusDays(5), 3, 3, 0, 0));

    assertThat(registry.get("daynomy.stock.price.data.age_days").gauge().value()).isZero();
    assertThat(registry.get("daynomy.stock.price.sync.latest_received_count").gauge().value())
        .isEqualTo(3);
  }
}
