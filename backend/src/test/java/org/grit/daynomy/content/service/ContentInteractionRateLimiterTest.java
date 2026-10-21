package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.junit.jupiter.api.Test;

class ContentInteractionRateLimiterTest {

  @Test
  void rejectsRequestsOverActorLimit() {
    ContentInteractionRateLimiter limiter =
        new ContentInteractionRateLimiter(
            Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC), 1);
    limiter.acquire("anonymous:visitor");

    assertThatThrownBy(() -> limiter.acquire("anonymous:visitor"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ContentErrorCode.CONTENT_INTERACTION_RATE_LIMITED);
  }

  @Test
  void appliesLimitSeparatelyForEachActor() {
    ContentInteractionRateLimiter limiter =
        new ContentInteractionRateLimiter(
            Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC), 1);

    limiter.acquire("anonymous:first");
    limiter.acquire("anonymous:second");
  }
}
