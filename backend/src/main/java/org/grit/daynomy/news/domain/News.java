package org.grit.daynomy.news.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "news")
public class News extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "sources", columnDefinition = "jsonb", nullable = false)
  private List<NewsSourceInfo> sources;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private Category category;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private NewsStatus status;

  private News(
      String title,
      String content,
      String imageUrl,
      List<NewsSourceInfo> sources,
      Category category,
      Instant publishedAt,
      NewsStatus status) {
    this.title = title;
    this.content = content;
    this.imageUrl = imageUrl;
    this.sources = List.copyOf(sources);
    this.category = category;
    this.publishedAt = publishedAt;
    this.status = status;
  }

  public static News createPublished(
      String title,
      String content,
      String imageUrl,
      List<NewsSourceInfo> sources,
      Category category,
      Instant publishedAt) {
    return new News(title, content, imageUrl, sources, category, publishedAt, NewsStatus.PUBLISHED);
  }

  public static News createDraft(
      String title,
      String content,
      String imageUrl,
      List<NewsSourceInfo> sources,
      Category category) {
    return new News(title, content, imageUrl, sources, category, null, NewsStatus.DRAFT);
  }

  public void update(
      String title,
      String content,
      String imageUrl,
      List<NewsSourceInfo> sources,
      Category category) {
    this.title = title;
    this.content = content;
    this.imageUrl = imageUrl;
    this.sources = List.copyOf(sources);
    this.category = category;
  }

  public void publish() {
    if (status != NewsStatus.DRAFT) {
      throw new BusinessException(NewsErrorCode.NEWS_NOT_DRAFT);
    }

    this.status = NewsStatus.PUBLISHED;
    this.publishedAt = Instant.now();
  }

  public void reject() {
    if (status != NewsStatus.DRAFT) {
      throw new BusinessException(NewsErrorCode.NEWS_NOT_DRAFT);
    }

    this.status = NewsStatus.REJECTED;
    this.publishedAt = null;
  }

  public void delete() {
    this.status = NewsStatus.DELETED;
    this.publishedAt = null;
  }

  public boolean isPublished() {
    return status == NewsStatus.PUBLISHED;
  }

  public boolean isDraft() {
    return status == NewsStatus.DRAFT;
  }
}
