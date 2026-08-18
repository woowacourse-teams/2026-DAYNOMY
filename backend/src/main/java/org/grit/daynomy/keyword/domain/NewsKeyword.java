package org.grit.daynomy.keyword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class NewsKeyword {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", nullable = false)
  private News news;

  @Column(name = "keyword", nullable = false)
  private String keyword;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  public NewsKeyword(String keyword, String description) {
    this.keyword = keyword;
    this.description = description;
  }

  public NewsKeyword(News news, String keyword, String description) {
    this.news = news;
    this.keyword = keyword;
    this.description = description;
  }
}
