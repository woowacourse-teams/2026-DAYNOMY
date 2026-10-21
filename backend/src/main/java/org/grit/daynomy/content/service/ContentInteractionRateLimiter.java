package org.grit.daynomy.content.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContentInteractionRateLimiter {

  private static final int MAX_TRACKED_ACTORS = 10_000;

  private final Clock clock;
  private final int limitPerMinute;
  private final Map<String, ArrayDeque<Instant>> requests =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ArrayDeque<Instant>> eldest) {
          return size() > MAX_TRACKED_ACTORS;
        }
      };

  public ContentInteractionRateLimiter(
      Clock clock, @Value("${content-interaction.rate-limit-per-minute:60}") int limitPerMinute) {
    this.clock = clock;
    this.limitPerMinute = limitPerMinute;
  }

  public synchronized void acquire(String actor) {
    Instant now = clock.instant();
    ArrayDeque<Instant> actorRequests =
        requests.computeIfAbsent(actor, ignored -> new ArrayDeque<>());
    while (!actorRequests.isEmpty() && !actorRequests.peekFirst().isAfter(now.minusSeconds(60))) {
      actorRequests.removeFirst();
    }
    if (actorRequests.size() >= limitPerMinute) {
      throw new BusinessException(ContentErrorCode.CONTENT_INTERACTION_RATE_LIMITED);
    }
    actorRequests.addLast(now);
  }
}
