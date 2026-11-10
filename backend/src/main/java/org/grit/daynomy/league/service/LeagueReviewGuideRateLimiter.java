package org.grit.daynomy.league.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeagueReviewGuideRateLimiter {
  private static final int DAILY_LIMIT = 10;
  private static final int MAX_MEMBERS = 10_000;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final Clock clock;
  private final Map<Long, State> requests = new LinkedHashMap<>(16, 0.75f, true);

  public synchronized void acquire(Long memberId) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
    State state = requests.get(memberId);
    if (state == null) {
      if (requests.size() >= MAX_MEMBERS) {
        Iterator<State> states = requests.values().iterator();
        while (states.hasNext()) {
          if (!states.next().busy) {
            states.remove();
            break;
          }
        }
        if (requests.size() >= MAX_MEMBERS)
          throw new BusinessException(LeagueErrorCode.REVIEW_GUIDE_RATE_LIMITED);
      }
      state = new State(today);
      requests.put(memberId, state);
    }
    if (state.busy) throw new BusinessException(LeagueErrorCode.REVIEW_GUIDE_RATE_LIMITED);
    if (!state.date.equals(today)) {
      state.date = today;
      state.count = 0;
    }
    if (state.count >= DAILY_LIMIT)
      throw new BusinessException(LeagueErrorCode.REVIEW_GUIDE_RATE_LIMITED);
    state.count++;
    state.busy = true;
  }

  public synchronized void release(Long memberId) {
    State state = requests.get(memberId);
    if (state != null) state.busy = false;
  }

  private static final class State {
    private LocalDate date;
    private int count;
    private boolean busy;

    private State(LocalDate date) {
      this.date = date;
    }
  }
}
