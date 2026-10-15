package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.AssetImpactResult;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_asset_analyses")
public class PortfolioAssetAnalysis extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_name", nullable = false, length = 100)
  private String assetName;

  @Column(name = "analysis_version", nullable = false, length = 100)
  private String analysisVersion;

  @Column(nullable = false, length = 100)
  private String model;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private AssetImpactResult snapshot;

  @Column(name = "analyzed_at", nullable = false)
  private Instant analyzedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
