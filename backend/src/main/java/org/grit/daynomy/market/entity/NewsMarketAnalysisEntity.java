package org.grit.daynomy.market.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class NewsMarketAnalysisEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", nullable = false, unique = true)
  private News news;

  @Column(name = "cause", columnDefinition = "TEXT", nullable = false)
  private String cause;

  @ElementCollection
  @CollectionTable(
      name = "news_market_analysis_assets",
      joinColumns = @JoinColumn(name = "news_market_analysis_id"))
  @OrderColumn(name = "sort_order")
  private List<AssetImpactEntity> assets = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "news_market_analysis_scenarios",
      joinColumns = @JoinColumn(name = "news_market_analysis_id"))
  @OrderColumn(name = "sort_order")
  private List<ScenarioEntity> scenarios = new ArrayList<>();

  public NewsMarketAnalysisEntity(
      News news, String cause, List<AssetImpactEntity> assets, List<ScenarioEntity> scenarios) {
    this.news = news;
    this.cause = cause;
    this.assets = new ArrayList<>(assets);
    this.scenarios = new ArrayList<>(scenarios);
  }
}
