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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_analysis_evidence")
public class PortfolioAnalysisEvidence {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_id", nullable = false, updatable = false)
  private PortfolioAnalysis analysis;

  // null은 특정 종목에 연결되지 않은 전체 분석 출처를 뜻한다.
  @Column(name = "asset_name", updatable = false, length = 100)
  private String assetName;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String title;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String url;

  @Column(name = "published_on", updatable = false)
  private LocalDate publishedOn;

  @Column(name = "occurred_on", updatable = false)
  private LocalDate occurredOn;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String excerpt;

  public PortfolioAnalysisEvidence(
      PortfolioAnalysis analysis,
      String assetName,
      String title,
      String url,
      LocalDate publishedOn,
      LocalDate occurredOn,
      String excerpt) {
    this.analysis = analysis;
    this.assetName = assetName;
    this.title = title;
    this.url = url;
    this.publishedOn = publishedOn;
    this.occurredOn = occurredOn;
    this.excerpt = excerpt;
  }
}
