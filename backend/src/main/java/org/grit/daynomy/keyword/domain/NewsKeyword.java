package org.grit.daynomy.keyword.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class NewsKeyword extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", nullable = false)
  private News news;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private KeywordCategory category;

  @Column(name = "keyword", nullable = false)
  private String keyword;

  @Column(name = "point1", columnDefinition = "TEXT", nullable = false)
  private String point1;

  @Column(name = "point2", columnDefinition = "TEXT", nullable = false)
  private String point2;

  @Column(name = "point3", columnDefinition = "TEXT", nullable = false)
  private String point3;

  public NewsKeyword(
      KeywordCategory category, String keyword, String point1, String point2, String point3) {
    this.category = category;
    this.keyword = keyword;
    this.point1 = point1;
    this.point2 = point2;
    this.point3 = point3;
  }

  public NewsKeyword(
      News news,
      KeywordCategory category,
      String keyword,
      String point1,
      String point2,
      String point3) {
    this.news = news;
    this.category = category;
    this.keyword = keyword;
    this.point1 = point1;
    this.point2 = point2;
    this.point3 = point3;
  }
}
