package org.grit.daynomy.portfolio.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAssetAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PortfolioAssetAnalysisRepository
    extends JpaRepository<PortfolioAssetAnalysis, Long> {
  List<PortfolioAssetAnalysis> findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
      List<String> assetNames, String analysisVersion, String model, Instant now);

  // 동시 저장은 허용하되, 먼저 완료된 오래된 결과가 최신 결과를 덮어쓰지 않는다.
  @Transactional
  @Modifying
  @Query(
      value =
          """
      INSERT INTO portfolio_asset_analyses
          (asset_name, analysis_version, model, snapshot, analyzed_at, expires_at, created_at, updated_at)
      VALUES (:assetName, :analysisVersion, :model, CAST(:snapshot AS jsonb),
          :analyzedAt, :expiresAt, :analyzedAt, :analyzedAt)
      ON CONFLICT (asset_name, analysis_version, model) DO UPDATE
      SET snapshot = EXCLUDED.snapshot, analyzed_at = EXCLUDED.analyzed_at,
          expires_at = EXCLUDED.expires_at, updated_at = EXCLUDED.updated_at
      WHERE portfolio_asset_analyses.analyzed_at < EXCLUDED.analyzed_at
      """,
      nativeQuery = true)
  void upsert(
      String assetName,
      String analysisVersion,
      String model,
      String snapshot,
      Instant analyzedAt,
      Instant expiresAt);
}
