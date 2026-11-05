package org.grit.daynomy.pmfsurvey.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.pmfsurvey.domain.PmfAnswer;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity.Cohort;

public record PmfSurveyResultsResponse(
    long surveyId, long totalResponses, String source, List<Group> groups) {

  public record Group(
      Cohort cohort,
      long responses,
      Map<PmfAnswer, Long> answers,
      OffsetDateTime firstRespondedAt,
      OffsetDateTime lastRespondedAt) {}
}
