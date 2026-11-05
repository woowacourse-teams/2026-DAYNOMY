package org.grit.daynomy.pmfsurvey.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity.Cohort;
import org.junit.jupiter.api.Test;

class PmfSurveyPolicyTest {

  private final Instant respondedAt = Instant.parse("2026-11-05T15:00:00Z");
  private final Instant windowStart = respondedAt.minus(14, ChronoUnit.DAYS);

  @Test
  void classifiesRequestsAtTheFourteenDayBoundary() {
    assertThat(new PmfSurveyActivity(windowStart, null, windowStart).cohortAt(respondedAt))
        .isEqualTo(Cohort.OFFICIAL_PMF);
    assertThat(
            new PmfSurveyActivity(windowStart, null, windowStart.minusSeconds(1))
                .cohortAt(respondedAt))
        .isEqualTo(Cohort.OTHER);
  }

  @Test
  void distinguishesObservedAbsenceFromMissingHistory() {
    assertThat(new PmfSurveyActivity(windowStart, null, null).cohortAt(respondedAt))
        .isEqualTo(Cohort.OTHER);
    assertThat(new PmfSurveyActivity(windowStart.plusSeconds(1), null, null).cohortAt(respondedAt))
        .isEqualTo(Cohort.HISTORY_UNKNOWN);
  }

  @Test
  void snoozesToNextKoreanMidnightOnBothSidesOfMidnight() {
    assertThat(PmfSurveyService.nextMidnight(respondedAt.minusSeconds(1)))
        .isEqualTo(OffsetDateTime.parse("2026-11-06T00:00:00+09:00"));
    assertThat(PmfSurveyService.nextMidnight(respondedAt))
        .isEqualTo(OffsetDateTime.parse("2026-11-07T00:00:00+09:00"));
  }
}
