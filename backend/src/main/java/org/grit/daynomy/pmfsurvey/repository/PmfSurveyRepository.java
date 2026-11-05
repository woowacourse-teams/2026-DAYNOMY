package org.grit.daynomy.pmfsurvey.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.pmfsurvey.domain.PmfAnswer;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity;
import org.grit.daynomy.pmfsurvey.domain.PmfSurveyActivity.Cohort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PmfSurveyRepository {

  private final JdbcClient jdbc;

  public void recordVisit(String respondent, String session, OffsetDateTime now) {
    jdbc.sql(
            """
        INSERT INTO pmf_survey_visitors (respondent_key, first_observed_at, first_visit_session)
        VALUES (:respondent, :now, :session)
        ON CONFLICT (respondent_key) DO NOTHING
        """)
        .param("respondent", respondent)
        .param("now", now)
        .param("session", session)
        .update();
  }

  public void recordAnalysisRequest(String respondent, String session, OffsetDateTime now) {
    jdbc.sql(
            """
        INSERT INTO pmf_survey_visitors
            (respondent_key, first_observed_at, first_visit_session, last_analysis_request_at)
        VALUES (:respondent, :now, :session, :now)
        ON CONFLICT (respondent_key) DO UPDATE SET
            last_analysis_request_at = GREATEST(
                pmf_survey_visitors.last_analysis_request_at, EXCLUDED.last_analysis_request_at
            )
        """)
        .param("respondent", respondent)
        .param("now", now)
        .param("session", session)
        .update();
  }

  public Optional<PmfSurveyActivity> activity(String respondent) {
    return jdbc.sql(
            """
        SELECT first_observed_at, first_visit_session, last_analysis_request_at
        FROM pmf_survey_visitors WHERE respondent_key = :respondent
        """)
        .param("respondent", respondent)
        .query(
            (rs, row) -> {
              OffsetDateTime lastAnalysis =
                  rs.getObject("last_analysis_request_at", OffsetDateTime.class);
              return new PmfSurveyActivity(
                  rs.getObject("first_observed_at", OffsetDateTime.class).toInstant(),
                  rs.getString("first_visit_session"),
                  lastAnalysis == null ? null : lastAnalysis.toInstant());
            })
        .optional();
  }

  public boolean hasEarlierMemberUse(long memberId, OffsetDateTime firstObservedAt) {
    return jdbc.sql(
            """
        SELECT EXISTS (
            SELECT 1 FROM members m
            LEFT JOIN portfolios p ON p.member_id = m.id
            WHERE m.id = :memberId
              AND (
                  m.created_at < :firstObservedDay
                  OR EXISTS (SELECT 1 FROM portfolio_transactions t
                             WHERE t.portfolio_id = p.id AND t.created_at < :firstObservedAt)
                  OR EXISTS (SELECT 1 FROM portfolio_holdings h
                             WHERE h.portfolio_id = p.id AND h.created_at < :firstObservedAt)
                  OR EXISTS (SELECT 1 FROM portfolio_analyses a
                             WHERE a.portfolio_id = p.id AND a.created_at < :firstObservedAt)
              )
        )
        """)
        .param("memberId", memberId)
        .param("firstObservedAt", firstObservedAt)
        .param(
            "firstObservedDay",
            firstObservedAt.toLocalDate().atStartOfDay().atOffset(firstObservedAt.getOffset()))
        .query(Boolean.class)
        .single();
  }

  public Optional<Long> responseId(long surveyId, String respondent) {
    return jdbc.sql(
            """
        SELECT id FROM pmf_survey_responses
        WHERE survey_id = :surveyId AND respondent_key = :respondent
        """)
        .param("surveyId", surveyId)
        .param("respondent", respondent)
        .query(Long.class)
        .optional();
  }

  public Optional<Long> insertResponse(
      long surveyId,
      String respondent,
      PmfAnswer answer,
      String reason,
      OffsetDateTime respondedAt,
      Cohort cohort,
      OffsetDateTime analysisRequestAt) {
    return jdbc.sql(
            """
        INSERT INTO pmf_survey_responses
            (survey_id, respondent_key, answer, reason, responded_at, source, cohort, analysis_request_at)
        VALUES (:surveyId, :respondent, :answer, :reason, :respondedAt, 'IN_APP', :cohort, :analysisRequestAt)
        ON CONFLICT (survey_id, respondent_key) DO NOTHING
        RETURNING id
        """)
        .param("surveyId", surveyId)
        .param("respondent", respondent)
        .param("answer", answer.name())
        .param("reason", reason, Types.VARCHAR)
        .param("respondedAt", respondedAt)
        .param("cohort", cohort.name())
        .param("analysisRequestAt", analysisRequestAt, Types.TIMESTAMP_WITH_TIMEZONE)
        .query(Long.class)
        .optional();
  }

  public Optional<OffsetDateTime> hiddenUntil(long surveyId, String respondent) {
    return jdbc.sql(
            """
        SELECT hidden_until FROM pmf_survey_snoozes
        WHERE survey_id = :surveyId AND respondent_key = :respondent
        """)
        .param("surveyId", surveyId)
        .param("respondent", respondent)
        .query((rs, row) -> rs.getObject("hidden_until", OffsetDateTime.class))
        .optional();
  }

  public void snooze(long surveyId, String respondent, OffsetDateTime hiddenUntil) {
    jdbc.sql(
            """
        INSERT INTO pmf_survey_snoozes (survey_id, respondent_key, hidden_until)
        VALUES (:surveyId, :respondent, :hiddenUntil)
        ON CONFLICT (survey_id, respondent_key) DO UPDATE SET
            hidden_until = GREATEST(pmf_survey_snoozes.hidden_until, EXCLUDED.hidden_until)
        """)
        .param("surveyId", surveyId)
        .param("respondent", respondent)
        .param("hiddenUntil", hiddenUntil)
        .update();
  }

  public List<AnswerCount> answerCounts(long surveyId) {
    return jdbc.sql(
            """
        SELECT cohort, answer, COUNT(*) AS responses,
            MIN(responded_at) AS first_responded_at, MAX(responded_at) AS last_responded_at
        FROM pmf_survey_responses WHERE survey_id = :surveyId
        GROUP BY cohort, answer
        """)
        .param("surveyId", surveyId)
        .query(
            (rs, row) ->
                new AnswerCount(
                    Cohort.valueOf(rs.getString("cohort")),
                    PmfAnswer.valueOf(rs.getString("answer")),
                    rs.getLong("responses"),
                    rs.getObject("first_responded_at", OffsetDateTime.class),
                    rs.getObject("last_responded_at", OffsetDateTime.class)))
        .list();
  }

  public record AnswerCount(
      Cohort cohort,
      PmfAnswer answer,
      long responses,
      OffsetDateTime firstRespondedAt,
      OffsetDateTime lastRespondedAt) {}
}
