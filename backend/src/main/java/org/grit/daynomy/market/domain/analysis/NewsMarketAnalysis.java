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
  private String summary;

  public NewsMarketAnalysis(String summary) {
    this.summary = summary;
  }

  public NewsMarketAnalysis(News news, String summary) {
    this.news = news;
    this.summary = summary;
  }
}
