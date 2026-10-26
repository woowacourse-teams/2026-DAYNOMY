package org.grit.daynomy.emailsubscription.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 실행권과 비용 예약은 짧은 DB 트랜잭션으로 확보하며 HTTP 호출 중에는 유지하지 않는다. */
@Repository
public class EmailAnalysisExecutionRepository {
  @PersistenceContext private EntityManager entityManager;

  public record Job(UUID id, Long assetId, Instant asOf) {}

  @Transactional
  public void register(LocalDate date, Instant asOf, List<Long> assetIds) {
    // 여러 서버가 겹치는 종목을 등록할 때에도 같은 순서로 잠금을 얻는다.
    for (Long assetId : assetIds.stream().distinct().sorted().toList()) {
      entityManager
          .createNativeQuery(
              """
          INSERT INTO email_analysis_jobs(id, run_date, asset_id, as_of)
          VALUES (:id, :date, :asset, :asOf) ON CONFLICT(run_date, asset_id) DO NOTHING
          """)
          .setParameter("id", UUID.randomUUID())
          .setParameter("date", date)
          .setParameter("asset", assetId)
          .setParameter("asOf", asOf)
          .executeUpdate();
    }
  }

  @Transactional
  public Optional<Job> claim(LocalDate date, Instant now, EmailAnalysisPolicy policy) {
    lock();
    if (!policy.canStart(now) || !hasBudget(date, policy)) return Optional.empty();
    if (count("SELECT count(*) FROM email_analysis_jobs WHERE status = 'RUNNING'") >= 2
        || ((Number)
                    entityManager
                        .createNativeQuery(
                            "SELECT count(*) FROM email_analysis_jobs WHERE run_date = :date AND status <> 'QUEUED'")
                        .setParameter("date", date)
                        .getSingleResult())
                .longValue()
            >= 100) return Optional.empty();
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
        SELECT j.id, j.asset_id, j.as_of FROM email_analysis_jobs j
        WHERE j.run_date = :date AND j.status = 'QUEUED'
        ORDER BY (SELECT max(h.started_at) FROM email_analysis_jobs h WHERE h.asset_id = j.asset_id) NULLS FIRST,
            (SELECT min(h.run_date) FROM email_analysis_jobs h WHERE h.asset_id = j.asset_id), j.asset_id
        LIMIT 1
        """)
            .unwrap(NativeQuery.class)
            .addScalar("id", UUID.class)
            .addScalar("asset_id", Long.class)
            .addScalar("as_of", Instant.class)
            .setParameter("date", date)
            .getResultList();
    if (rows.isEmpty()) return Optional.empty();
    Object[] row = rows.getFirst();
    Job next = new Job((UUID) row[0], (Long) row[1], (Instant) row[2]);
    entityManager
        .createNativeQuery(
            "UPDATE email_analysis_jobs SET status = 'RUNNING', started_at = :now WHERE id = :id")
        .setParameter("now", now)
        .setParameter("id", next.id())
        .executeUpdate();
    return Optional.of(next);
  }

  @Transactional(readOnly = true)
  public boolean hasSubscriberHolding(Long assetId) {
    return (Boolean)
        entityManager
            .createNativeQuery(
                """
        SELECT EXISTS(SELECT 1 FROM portfolio_holdings h JOIN portfolios p ON p.id = h.portfolio_id
          JOIN email_subscriptions s ON s.member_id = p.member_id JOIN members m ON m.id = s.member_id
          WHERE h.asset_id = :asset AND s.status = 'SUBSCRIBED' AND m.withdrawn_at IS NULL)
        """)
            .setParameter("asset", assetId)
            .getSingleResult();
  }

  @Transactional
  public void startAttempt(UUID jobId) {
    if (entityManager
            .createNativeQuery(
                "UPDATE email_analysis_jobs SET attempts = attempts + 1 WHERE id = :id AND status = 'RUNNING' AND attempts < 2")
            .setParameter("id", jobId)
            .executeUpdate()
        != 1) throw new IllegalStateException("분석 재시도 한도를 초과했습니다.");
  }

  @Transactional
  public UUID reserve(
      Job job, String phase, String model, Instant now, EmailAnalysisPolicy policy) {
    lock();
    LocalDate date = now.atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    boolean running =
        (Boolean)
            entityManager
                .createNativeQuery(
                    "SELECT EXISTS(SELECT 1 FROM email_analysis_jobs WHERE id = :id AND status = 'RUNNING' AND run_date = :date)")
                .setParameter("id", job.id())
                .setParameter("date", date)
                .getSingleResult();
    if (!running
        || !policy.canStart(now)
        || !policy.model().equals(model)
        || !hasBudget(date, policy))
      throw new IllegalStateException("자동 분석 시간·비용·모델 제한으로 요청을 중단했습니다.");
    UUID id = UUID.randomUUID();
    entityManager
        .createNativeQuery(
            """
        INSERT INTO email_analysis_calls(id, job_id, phase, model, started_at, reserved_usd, charged_usd)
        VALUES (:id, :job, :phase, :model, :now, :amount, :amount)
        """)
        .setParameter("id", id)
        .setParameter("job", job.id())
        .setParameter("phase", phase)
        .setParameter("model", model)
        .setParameter("now", now)
        .setParameter("amount", policy.reserveUsd())
        .executeUpdate();
    return id;
  }

  @Transactional
  public void completeCall(
      UUID id,
      String status,
      Long input,
      Long cached,
      Long output,
      Long searches,
      BigDecimal estimated,
      Integer httpStatus,
      Instant now) {
    lock();
    entityManager
        .createNativeQuery(
            """
        UPDATE email_analysis_calls SET status = :status, completed_at = :now,
          input_tokens = cast(:input AS BIGINT), cached_tokens = cast(:cached AS BIGINT),
          output_tokens = cast(:output AS BIGINT), search_calls = cast(:searches AS BIGINT),
          estimated_usd = cast(:estimated AS NUMERIC), charged_usd = coalesce(cast(:estimated AS NUMERIC), reserved_usd),
          http_status = cast(:http AS INTEGER) WHERE id = :id AND status = 'IN_FLIGHT'
        """)
        .unwrap(NativeQuery.class)
        .setParameter("id", id)
        .setParameter("status", status)
        .setParameter("now", now)
        .setParameter("input", input, Long.class)
        .setParameter("cached", cached, Long.class)
        .setParameter("output", output, Long.class)
        .setParameter("searches", searches, Long.class)
        .setParameter("estimated", estimated, BigDecimal.class)
        .setParameter("http", httpStatus, Integer.class)
        .executeUpdate();
  }

  @Transactional
  public void finish(UUID id, String status, Long analysisId, Instant now) {
    entityManager
        .createNativeQuery(
            "UPDATE email_analysis_jobs SET status = :status, analysis_id = cast(:analysis AS BIGINT), completed_at = :now WHERE id = :id AND status = 'RUNNING'")
        .unwrap(NativeQuery.class)
        .setParameter("id", id)
        .setParameter("status", status)
        .setParameter("analysis", analysisId, Long.class)
        .setParameter("now", now)
        .executeUpdate();
  }

  private boolean hasBudget(LocalDate date, EmailAnalysisPolicy policy) {
    Instant day = date.atStartOfDay(EmailAnalysisPolicy.ZONE).toInstant();
    Instant month = date.withDayOfMonth(1).atStartOfDay(EmailAnalysisPolicy.ZONE).toInstant();
    Object[] totals =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
        SELECT coalesce(sum(charged_usd) FILTER (WHERE started_at >= :day), 0) AS day_total,
            coalesce(sum(charged_usd), 0) AS month_total,
            coalesce(max(estimated_usd), 0) AS largest_call FROM email_analysis_calls WHERE started_at >= :month
        """)
                .setParameter("day", day)
                .setParameter("month", month)
                .getSingleResult();
    return ((BigDecimal) totals[2]).compareTo(policy.reserveUsd()) <= 0
        && ((BigDecimal) totals[0]).add(policy.reserveUsd()).compareTo(policy.dailyUsd()) <= 0
        && ((BigDecimal) totals[1]).add(policy.reserveUsd()).compareTo(policy.monthlyUsd()) <= 0;
  }

  private long count(String sql) {
    return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
  }

  private void lock() {
    entityManager
        .createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(5620700)")
        .getSingleResult();
  }
}
