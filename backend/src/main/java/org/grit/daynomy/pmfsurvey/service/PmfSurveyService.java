package org.grit.daynomy.pmfsurvey.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.pmfsurvey.domain.PmfAnswer;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity.Cohort;
import org.grit.daynomy.pmfsurvey.dto.PmfSubmissionRequest;
import org.grit.daynomy.pmfsurvey.dto.PmfSurveyResponse;
import org.grit.daynomy.pmfsurvey.dto.PmfSurveyResultsResponse;
import org.grit.daynomy.pmfsurvey.dto.PmfSurveyResultsResponse.Group;
import org.grit.daynomy.pmfsurvey.exception.PmfSurveyErrorCode;
import org.grit.daynomy.pmfsurvey.repository.PmfSurveyRepository;
import org.grit.daynomy.pmfsurvey.repository.PmfSurveyRepository.AnswerCount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PmfSurveyService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final long MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L;
  private final PmfSurveyRepository repository;
  private final long activeSurveyId;

  public PmfSurveyService(
      PmfSurveyRepository repository, @Value("${pmf-survey.active-id}") long activeSurveyId) {
    if (activeSurveyId < 0 || activeSurveyId > MAX_SAFE_JS_INTEGER) {
      throw new IllegalArgumentException("pmf-survey.active-id is outside the supported range");
    }
    this.repository = repository;
    this.activeSurveyId = activeSurveyId;
  }

  public long activeSurveyId() {
    return activeSurveyId;
  }

  @Transactional
  public PmfSurveyResponse current(String respondent, String session) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    repository.recordVisit(respondent, session, now);
    PmfSurveyActivity activity = repository.activity(respondent).orElseThrow();
    return new PmfSurveyResponse(
        activeSurveyId,
        true,
        repository.responseId(activeSurveyId, respondent).orElse(null),
        activity.isFirstVisit(session),
        repository
            .hiddenUntil(activeSurveyId, respondent)
            .filter(until -> until.isAfter(now))
            .orElse(null));
  }

  public void recordAnalysisRequest(String respondent, String session) {
    repository.recordAnalysisRequest(respondent, session, OffsetDateTime.now(ZoneOffset.UTC));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Submission submit(long surveyId, String respondent, PmfSubmissionRequest request) {
    requireActiveSurvey(surveyId);
    PmfSurveyActivity activity = requireVisit(respondent);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Instant analysisRequest = activity.lastAnalysisRequestAt();

    return repository
        .insertResponse(
            surveyId,
            respondent,
            request.answer(),
            request.reason(),
            now,
            activity.cohortAt(now.toInstant()),
            analysisRequest == null ? null : analysisRequest.atOffset(ZoneOffset.UTC))
        .map(id -> new Submission(id, true))
        .orElseGet(
            () -> new Submission(repository.responseId(surveyId, respondent).orElseThrow(), false));
  }

  @Transactional
  public void snooze(long surveyId, String respondent) {
    requireActiveSurvey(surveyId);
    requireVisit(respondent);
    repository.snooze(surveyId, respondent, nextMidnight(Instant.now()));
  }

  static OffsetDateTime nextMidnight(Instant now) {
    return now.atZone(SEOUL).toLocalDate().plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
  }

  public PmfSurveyResultsResponse results(long surveyId) {
    List<AnswerCount> counts = repository.answerCounts(surveyId);
    List<Group> groups =
        Arrays.stream(Cohort.values()).map(cohort -> group(cohort, counts)).toList();
    long total = groups.stream().mapToLong(Group::responses).sum();
    return new PmfSurveyResultsResponse(surveyId, total, "IN_APP", groups);
  }

  private Group group(Cohort cohort, List<AnswerCount> counts) {
    List<AnswerCount> rows = counts.stream().filter(row -> row.cohort() == cohort).toList();
    EnumMap<PmfAnswer, Long> byAnswer = new EnumMap<>(PmfAnswer.class);
    for (PmfAnswer answer : PmfAnswer.values()) {
      byAnswer.put(answer, 0L);
    }
    rows.forEach(row -> byAnswer.put(row.answer(), row.responses()));
    long total = rows.stream().mapToLong(AnswerCount::responses).sum();
    OffsetDateTime first =
        rows.stream()
            .map(AnswerCount::firstRespondedAt)
            .min(Comparator.naturalOrder())
            .orElse(null);
    OffsetDateTime last =
        rows.stream().map(AnswerCount::lastRespondedAt).max(Comparator.naturalOrder()).orElse(null);
    return new Group(cohort, total, byAnswer, first, last);
  }

  private void requireActiveSurvey(long surveyId) {
    if (surveyId <= 0 || surveyId != activeSurveyId) {
      throw new BusinessException(PmfSurveyErrorCode.PMF_SURVEY_NOT_FOUND);
    }
  }

  private PmfSurveyActivity requireVisit(String respondent) {
    return repository
        .activity(respondent)
        .orElseThrow(() -> new BusinessException(PmfSurveyErrorCode.PMF_SURVEY_VISITOR_REQUIRED));
  }

  public record Submission(long id, boolean created) {}
}
