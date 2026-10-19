package org.grit.daynomy.emailsubscription.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommonStockAnalysisRepository extends JpaRepository<CommonStockAnalysis, Long> {

  @Query(
      """
      select a from CommonStockAnalysis a
      where a.asset.id = :#{#key.assetId()} and a.model = :#{#key.model()}
        and a.analysisVersion = :#{#key.analysisVersion()}
        and a.evidenceVersion = :#{#key.evidenceVersion()} and a.asOf = :#{#key.asOf()}
      """)
  Optional<CommonStockAnalysis> findByKey(@Param("key") CommonStockAnalysisKey key);

  @Query(
      """
      select a from CommonStockAnalysis a join a.asset asset
      where asset.id = :#{#key.assetId()} and a.model = :#{#key.model()}
        and a.analysisVersion = :#{#key.analysisVersion()}
        and a.evidenceVersion = :#{#key.evidenceVersion()} and a.asOf = :#{#key.asOf()}
        and a.status = org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis$Status.VERIFIED
        and a.assetCode = asset.assetCode and a.market = asset.market and a.category = asset.category
      """)
  Optional<CommonStockAnalysis> findReusable(@Param("key") CommonStockAnalysisKey key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from CommonStockAnalysis a where a.id = :id")
  Optional<CommonStockAnalysis> findByIdForUpdate(@Param("id") Long id);

  // 동일 키의 최초 결과를 보존한다. 뒤늦은 중복 저장으로 검증된 내용을 바꾸지 않는다.
  @Modifying
  @Query(
      value =
          """
          INSERT INTO email_common_stock_analyses
              (asset_id, asset_code, market, category, model, analysis_version, evidence_version,
               as_of, content, content_hash, status, analyzed_at, created_at, updated_at)
          VALUES (:assetId, :assetCode, :market, :category, :model, :analysisVersion, :evidenceVersion,
                  :asOf, CAST(:content AS jsonb), :contentHash, 'PENDING', :analyzedAt, :analyzedAt, :analyzedAt)
          ON CONFLICT (asset_id, model, analysis_version, evidence_version, as_of) DO NOTHING
          """,
      nativeQuery = true)
  int insertPending(
      Long assetId,
      String assetCode,
      String market,
      String category,
      String model,
      String analysisVersion,
      String evidenceVersion,
      Instant asOf,
      String content,
      String contentHash,
      Instant analyzedAt);
}
