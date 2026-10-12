package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_analyses")
public class PortfolioAnalysis extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false, updatable = false)
  private Portfolio portfolio;

  @Column(name = "previous_analysis_id", updatable = false)
  private Long previousAnalysisId;

  @Column(name = "schema_version", nullable = false, updatable = false)
  private int schemaVersion;

  @Column(name = "analysis_version", nullable = false, updatable = false, length = 100)
  private String analysisVersion;

  @Column(nullable = false, updatable = false, length = 100)
  private String model;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private PortfolioAnalysisSnapshot snapshot;

  @Column(name = "analyzed_at", nullable = false, updatable = false)
  private Instant analyzedAt;

  @Column(name = "change_reason", nullable = false, updatable = false, columnDefinition = "text")
  private String changeReason;

  private PortfolioAnalysis(
      Portfolio portfolio,
      PortfolioAnalysis previous,
      String analysisVersion,
      String model,
      PortfolioAnalysisSnapshot snapshot,
      Instant analyzedAt,
      String changeReason) {
    this.portfolio = Objects.requireNonNull(portfolio);
    analyzedAt = Objects.requireNonNull(analyzedAt).truncatedTo(ChronoUnit.MICROS);
    if (previous != null) {
      if (previous.getId() == null
          || !Objects.equals(portfolio.getId(), previous.getPortfolio().getId())) {
        throw new IllegalArgumentException("같은 포트폴리오에 저장된 분석만 갱신할 수 있습니다.");
      }
      if (analyzedAt.isBefore(previous.getAnalyzedAt())) {
        throw new IllegalArgumentException("새 분석 시각은 이전 분석 시각보다 빠를 수 없습니다.");
      }
    }
    this.previousAnalysisId = previous == null ? null : previous.getId();
    this.schemaVersion = 1;
    this.analysisVersion = Objects.requireNonNull(analysisVersion);
    this.model = Objects.requireNonNull(model);
    this.snapshot = Objects.requireNonNull(snapshot);
    this.analyzedAt = Objects.requireNonNull(analyzedAt);
    this.changeReason = Objects.requireNonNull(changeReason);
  }

  public static PortfolioAnalysis create(
      Portfolio portfolio,
      PortfolioAnalysis previous,
      String analysisVersion,
      String model,
      PortfolioAnalysisSnapshot snapshot,
      Instant analyzedAt,
      String changeReason) {
    return new PortfolioAnalysis(
        portfolio, previous, analysisVersion, model, snapshot, analyzedAt, changeReason);
  }
}
