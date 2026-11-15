package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisShareRateLimiterTest {

  @Test
  void limitsRepeatedReadsForTheSameActor() {
    var limiter =
        new PortfolioAnalysisShareRateLimiter(
            Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC), 1, 1);
    limiter.acquireRead("127.0.0.1");

    assertThatThrownBy(() -> limiter.acquireRead("127.0.0.1"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_RATE_LIMITED));
  }
}
