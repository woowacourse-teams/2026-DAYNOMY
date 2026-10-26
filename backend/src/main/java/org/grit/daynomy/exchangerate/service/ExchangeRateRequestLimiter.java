package org.grit.daynomy.exchangerate.service;

import java.time.*;
import java.util.ArrayDeque;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.exchangerate.ExchangeRateErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeRateRequestLimiter {
  private final Clock exchangeRateClock;
  private final ArrayDeque<Instant> requests = new ArrayDeque<>();

  // Global per-instance budget also bounds requests spread across arbitrary dates/IPs.
  public synchronized void acquire() {
    Instant now = exchangeRateClock.instant();
    while (!requests.isEmpty() && !requests.peekFirst().isAfter(now.minusSeconds(60)))
      requests.removeFirst();
    if (requests.size() >= 120)
      throw new BusinessException(ExchangeRateErrorCode.EXCHANGE_RATE_RATE_LIMITED);
    requests.addLast(now);
  }
}
