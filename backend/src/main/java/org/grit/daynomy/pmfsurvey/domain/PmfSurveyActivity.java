package org.grit.daynomy.pmfsurvey.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record PmfSurveyActivity(
    Instant firstObservedAt, String firstVisitSession, Instant lastAnalysisRequestAt) {

  public enum Cohort {
    OFFICIAL_PMF,
    OTHER,
    HISTORY_UNKNOWN
  }

  public boolean isFirstVisit(String session) {
    return firstVisitSession.equals(session);
  }

  public Cohort cohortAt(Instant respondedAt) {
    Instant from = respondedAt.minus(14, ChronoUnit.DAYS);
    if (lastAnalysisRequestAt != null
        && !lastAnalysisRequestAt.isBefore(from)
        && !lastAnalysisRequestAt.isAfter(respondedAt)) {
      return Cohort.OFFICIAL_PMF;
    }
    if (!firstObservedAt.isAfter(from)) {
      return Cohort.OTHER;
    }
    return Cohort.HISTORY_UNKNOWN;
  }
}
