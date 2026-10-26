package org.grit.daynomy.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeagueParticipationTest {
  @Test
  void backfillIsLimitedToTheEnrollmentWeekAndKeepsActualEnrollmentTime() {
    Instant joined = Instant.parse("2026-10-21T01:00:00Z");
    var participation = new LeagueParticipation(null, joined);
    Instant monday = Instant.parse("2026-10-19T06:30:00Z");
    participation.includeCurrentWeekFrom(monday);
    assertThat(participation.getStartedAt()).isEqualTo(joined);
    assertThat(participation.contains(monday)).isTrue();
    assertThat(participation.contains(monday.minusSeconds(1))).isFalse();
    assertThatThrownBy(() -> participation.includeCurrentWeekFrom(monday.minusSeconds(86400)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "2026-09-23T01:00:00Z, 2026-09-23",
    "2026-09-23T06:30:00Z, 2026-09-23",
    "2026-09-23T06:30:01Z, 2026-09-24",
    "2026-09-25T08:00:00Z, 2026-09-26"
  })
  void eligibilityStartsAtTheFirstPossibleCloseInsteadOfNextMonday(
      String started, String expected) {
    var participation = new LeagueParticipation(null, Instant.parse(started));
    assertThat(participation.eligibleFrom()).isEqualTo(LocalDate.parse(expected));
  }
}
