package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "portfolio_asset_impact",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_portfolio_asset_impact_news_bookmark",
            columnNames = {"news_id", "bookmark_id"}))
public class PortfolioAssetImpact extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", nullable = false)
  private News news;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "bookmark_id", nullable = false)
  private Long bookmarkId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false)
  private ImpactDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "impact_level", nullable = false)
  private ImpactLevel impactLevel;

  @Column(name = "expected_reaction", columnDefinition = "TEXT", nullable = false)
  private String expectedReaction;

  @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
  private String reason;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public PortfolioAssetImpact(
      News news,
      Long assetId,
      Long bookmarkId,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String expectedReaction,
      String reason,
      int sortOrder) {
    this.news = news;
    this.assetId = assetId;
    this.bookmarkId = bookmarkId;
    this.direction = direction;
    this.impactLevel = impactLevel;
    this.expectedReaction = expectedReaction;
    this.reason = reason;
    this.sortOrder = sortOrder;
  }
}
