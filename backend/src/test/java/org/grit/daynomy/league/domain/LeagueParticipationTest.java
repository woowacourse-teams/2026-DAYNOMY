package org.grit.daynomy.league.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeagueParticipationTest {
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
