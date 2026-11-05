package org.grit.daynomy.pmfsurvey.dto;

import java.time.OffsetDateTime;

public record PmfSurveyResponse(
    long surveyId,
    boolean eligible,
    Long responseId,
    boolean isFirstVisit,
    OffsetDateTime hiddenUntil) {}
