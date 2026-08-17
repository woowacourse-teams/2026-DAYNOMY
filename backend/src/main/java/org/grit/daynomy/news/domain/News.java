package org.grit.daynomy.news.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "news",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_news_source_external_id",
            columnNames = {"source", "external_id"}))
public class News {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "description")
  private String description;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private NewsSource source;

  @Column(name = "external_id", nullable = false)
  private String externalId;

  @Column(name = "source_url", nullable = false)
  private String sourceUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private Category category;

  // TODO(choiyoung69): Keyword 관계 설계 확정 후 추가

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public News(
      String title,
      String content,
      String description,
      String imageUrl,
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      LocalDateTime publishedAt) {
    this.title = title;
    this.content = content;
    this.description = description;
    this.imageUrl = imageUrl;
    this.source = source;
    this.externalId = externalId;
    this.sourceUrl = sourceUrl;
    this.category = category;
    this.publishedAt = publishedAt;
  }
}
