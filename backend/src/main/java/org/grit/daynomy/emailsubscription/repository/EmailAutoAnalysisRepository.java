package org.grit.daynomy.emailsubscription.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** AI 호출 전에 실행 횟수를 예약한다. 실패·서버 중단도 사용한 실행량에 포함한다. */
@Repository
@RequiredArgsConstructor
public class EmailAutoAnalysisRepository {
  private final JdbcClient jdbc;

  public List<String> earningsSignals(List<Long> assetIds, Instant from, Instant cutoff) {
    if (assetIds.isEmpty()) return List.of();
    return jdbc.sql(
            """
        select 'earnings:' || a.id || ':' || e.source_key || ':' || extract(epoch from e.updated_at)
        from investment_events e join assets a on a.asset_code = e.related_asset_code
        where a.id in (:ids) and a.category = 'STOCK' and a.market in ('KOSPI','KOSDAQ')
          and e.event_type = 'CORPORATE_EARNINGS' and e.actual_value is not null
          and e.announced_at < :cutoff and e.updated_at >= :from and e.updated_at < :cutoff
          and e.source_key like 'DART-EARNINGS-%'
          and e.source_url like 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=%'
        order by e.announced_at desc, a.id
        """)
        .param("ids", assetIds)
        .param("from", java.sql.Timestamp.from(from))
        .param("cutoff", java.sql.Timestamp.from(cutoff))
        .query(String.class)
        .list();
  }

  @Transactional
  public Optional<UUID> claim(
      Long memberId,
      LocalDate date,
      List<String> signals,
      int dailyLimit,
      int monthlyLimit,
      Instant now) {
    if (signals.isEmpty() || dailyLimit <= 0 || monthlyLimit <= 0) return Optional.empty();
    // 여러 서버의 횟수 확인과 예약을 직렬화한다. 외부 AI 호출 동안에는 잠금을 유지하지 않는다.
    jdbc.sql("select pg_advisory_xact_lock(562697)").query((rs, row) -> true).single();
    var used =
        jdbc.sql(
                """
        select distinct signal from email_portfolio_analysis_runs r,
          lateral jsonb_array_elements_text(r.signals) signal
        where r.member_id = :memberId and signal in (:signals)
        """)
            .param("memberId", memberId)
            .param("signals", signals)
            .query(String.class)
            .list();
    var fresh = signals.stream().distinct().filter(signal -> !used.contains(signal)).toList();
    if (fresh.isEmpty()) return Optional.empty();
    var counts =
        jdbc.sql(
                """
        select count(*) filter (where run_date = :date) as daily,
          count(*) as monthly,
          count(*) filter (where run_date = :date and status = 'RUNNING') as running
        from email_portfolio_analysis_runs where run_date between :first and :date
        """)
            .param("date", date)
            .param("first", date.withDayOfMonth(1))
            .query(
                (rs, row) ->
                    new long[] {rs.getLong("daily"), rs.getLong("monthly"), rs.getLong("running")})
            .single();
    if (counts[0] >= dailyLimit || counts[1] >= monthlyLimit || counts[2] > 0)
      return Optional.empty();
    var id = UUID.randomUUID();
    var inserted =
        jdbc.sql(
                """
        insert into email_portfolio_analysis_runs(id, member_id, run_date, signals, status, started_at)
        values (:id, :memberId, :date, cast(:signals as jsonb), 'RUNNING', :now)
        on conflict (member_id, run_date) do nothing
        """)
            .param("id", id)
            .param("memberId", memberId)
            .param("date", date)
            .param("signals", json(fresh))
            .param("now", java.sql.Timestamp.from(now))
            .update();
    return inserted == 1 ? Optional.of(id) : Optional.empty();
  }

  @Transactional
  public void complete(UUID id, Long analysisId, boolean succeeded, Instant now) {
    jdbc.sql(
            """
        update email_portfolio_analysis_runs set status = :status,
          result_analysis_id = :analysisId, completed_at = :now
        where id = :id and status = 'RUNNING'
        """)
        .param("status", succeeded ? "SUCCEEDED" : "FAILED")
        .param("analysisId", analysisId, java.sql.Types.BIGINT)
        .param("now", java.sql.Timestamp.from(now))
        .param("id", id)
        .update();
  }

  private String json(List<String> signals) {
    try {
      return new ObjectMapper().writeValueAsString(signals);
    } catch (JsonProcessingException invalid) {
      throw new IllegalArgumentException("자동 분석 신호를 저장할 수 없습니다.", invalid);
    }
  }
}
