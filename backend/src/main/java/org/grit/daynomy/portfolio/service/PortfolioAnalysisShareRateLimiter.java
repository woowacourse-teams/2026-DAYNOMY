package org.grit.daynomy.portfolio.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortfolioAnalysisShareRateLimiter {

  private static final int MAX_TRACKED_ACTORS = 10_000;

  private final Clock clock;
  private final int readLimitPerMinute;
  private final int writeLimitPerMinute;
  // shortcut: 인스턴스별 메모리 제한이라 다중 서버 전역 제한이 아니다, 악용이 확인되면 Redis 기반 제한으로 교체한다.
  private final Map<String, ArrayDeque<Instant>> requests =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ArrayDeque<Instant>> eldest) {
          return size() > MAX_TRACKED_ACTORS;
        }
      };

  public PortfolioAnalysisShareRateLimiter(
      Clock clock,
      @Value("${portfolio-analysis-share.read-rate-limit-per-minute:120}") int readLimitPerMinute,
      @Value("${portfolio-analysis-share.write-rate-limit-per-minute:10}")
          int writeLimitPerMinute) {
    this.clock = clock;
    this.readLimitPerMinute = readLimitPerMinute;
    this.writeLimitPerMinute = writeLimitPerMinute;
  }

  public void acquireRead(String remoteAddress) {
    acquire("read:" + remoteAddress, readLimitPerMinute);
  }

  public void acquireWrite(Long memberId) {
    acquire("write:" + memberId, writeLimitPerMinute);
  }

  private synchronized void acquire(String actor, int limit) {
    Instant now = clock.instant();
    ArrayDeque<Instant> actorRequests =
        requests.computeIfAbsent(actor, ignored -> new ArrayDeque<>());
    while (!actorRequests.isEmpty() && !actorRequests.peekFirst().isAfter(now.minusSeconds(60))) {
      actorRequests.removeFirst();
    }
    if (actorRequests.size() >= limit) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_RATE_LIMITED);
    }
    actorRequests.addLast(now);
  }
}
