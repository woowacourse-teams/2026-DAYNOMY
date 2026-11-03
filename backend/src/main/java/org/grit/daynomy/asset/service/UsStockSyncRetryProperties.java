package org.grit.daynomy.asset.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock.us.sync.retry")
public record UsStockSyncRetryProperties(Integer maxAttempts, Duration delay) {
  public UsStockSyncRetryProperties {
    if (maxAttempts == null) maxAttempts = 3;
    if (delay == null) delay = Duration.ofMillis(250);
    if (maxAttempts < 1
        || maxAttempts > 5
        || delay.isNegative()
        || delay.compareTo(Duration.ofSeconds(5)) > 0) {
      throw new IllegalArgumentException("미국 종목 재시도는 1~5회, 대기 간격은 0~5초여야 합니다.");
    }
  }
}
