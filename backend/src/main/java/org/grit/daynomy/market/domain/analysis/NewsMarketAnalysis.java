package org.grit.daynomy.market.domain.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class NewsMarketAnalysis extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", nullable = false, unique = true)
  private News news;

  @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
  private String observedFact;

  @Column(name = "interpretation", columnDefinition = "TEXT", nullable = false)
  private String interpretation;

  @Column(name = "risk", columnDefinition = "TEXT", nullable = false)
  private String risk;

  @Column(name = "watch_point", columnDefinition = "TEXT", nullable = false)
  private String watchPoint;

  @Column(name = "limitation", columnDefinition = "TEXT", nullable = false)
  private String limitation;

  public NewsMarketAnalysis(String observedFact) {
    this.observedFact = observedFact;
    this.interpretation = "";
    this.risk = "";
    this.watchPoint = "";
    this.limitation = "";
  }

  public NewsMarketAnalysis(
      String observedFact,
      String interpretation,
      String risk,
      String watchPoint,
      String limitation) {
    this.observedFact = observedFact;
    this.interpretation = interpretation;
    this.risk = risk;
    this.watchPoint = watchPoint;
    this.limitation = limitation;
  }

  public NewsMarketAnalysis(
      News news,
      String observedFact,
      String interpretation,
      String risk,
      String watchPoint,
      String limitation) {
    this.news = news;
    this.observedFact = observedFact;
    this.interpretation = interpretation;
    this.risk = risk;
    this.watchPoint = watchPoint;
    this.limitation = limitation;
  }

  public NewsMarketAnalysis(News news, String observedFact) {
    this.news = news;
    this.observedFact = observedFact;
    this.interpretation = "";
    this.risk = "";
    this.watchPoint = "";
    this.limitation = "";
  }

  public void updateAnalysis(NewsMarketAnalysis marketAnalysis) {
    this.observedFact = marketAnalysis.observedFact;
    this.interpretation = marketAnalysis.interpretation;
    this.risk = marketAnalysis.risk;
    this.watchPoint = marketAnalysis.watchPoint;
    this.limitation = marketAnalysis.limitation;
  }
}
