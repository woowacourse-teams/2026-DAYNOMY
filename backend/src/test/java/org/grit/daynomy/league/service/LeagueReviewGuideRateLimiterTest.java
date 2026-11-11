package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.junit.jupiter.api.Test;

class LeagueReviewGuideRateLimiterTest {
  @Test
  void limitsDailyAttemptsAndConcurrentCalls() {
    var limiter =
        new LeagueReviewGuideRateLimiter(
            Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC));
    limiter.acquire(1L);
    assertThatThrownBy(() -> limiter.acquire(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_GUIDE_RATE_LIMITED));
    limiter.release(1L);
    for (int i = 1; i < 10; i++) {
      limiter.acquire(1L);
      limiter.release(1L);
    }
    assertThatThrownBy(() -> limiter.acquire(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_GUIDE_RATE_LIMITED));
  }
}
