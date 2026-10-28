package org.grit.daynomy.league.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public interface LeagueParticipationValue {
  Instant getStartedAt();

  Instant getEndedAt();

  Instant getPerformanceStartedAt();

  default Instant calculationStartedAt() {
    return getPerformanceStartedAt() == null ? getStartedAt() : getPerformanceStartedAt();
  }

  default boolean contains(Instant close) {
    return !close.isBefore(calculationStartedAt())
        && (getEndedAt() == null || close.isBefore(getEndedAt()));
  }

  default LocalDate eligibleFrom() {
    var started = calculationStartedAt().atZone(ZoneId.of("Asia/Seoul"));
    return started.toLocalTime().isAfter(java.time.LocalTime.of(15, 30))
        ? started.toLocalDate().plusDays(1)
        : started.toLocalDate();
  }
}
