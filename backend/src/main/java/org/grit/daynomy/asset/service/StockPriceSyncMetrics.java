package org.grit.daynomy.asset.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class StockPriceSyncMetrics {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
  private final AtomicLong latestBaseDateEpochDay = new AtomicLong();
  private final AtomicLong latestReceivedCount = new AtomicLong();

  public StockPriceSyncMetrics(MeterRegistry registry) {
    Gauge.builder(
            "daynomy.stock.price.sync.last_success_epoch_seconds",
            lastSuccessEpochSeconds,
            AtomicLong::get)
        .register(registry);
    Gauge.builder(
            "daynomy.stock.price.sync.latest_received_count", latestReceivedCount, AtomicLong::get)
        .register(registry);
    Gauge.builder("daynomy.stock.price.data.age_days", this, StockPriceSyncMetrics::dataAgeDays)
        .register(registry);
  }

  public void recordSuccess(StockPriceSyncResult result) {
    lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
    latestBaseDateEpochDay.accumulateAndGet(result.baseDate().toEpochDay(), Math::max);
    latestReceivedCount.set(result.receivedCount());
  }

  private double dataAgeDays() {
    long epochDay = latestBaseDateEpochDay.get();
    if (epochDay == 0) {
      return Double.NaN;
    }
    return ChronoUnit.DAYS.between(LocalDate.ofEpochDay(epochDay), LocalDate.now(SEOUL));
  }
}
