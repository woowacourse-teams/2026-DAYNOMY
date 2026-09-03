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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(
    name = "news",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_news_source_external_id",
            columnNames = {"source", "external_id"}))
public class News extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "source")
  private NewsSource source;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "source_url", columnDefinition = "TEXT", nullable = false)
  private String sourceUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private Category category;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private NewsStatus status;

  public News(
      String title,
      String content,
      String description,
      String imageUrl,
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      Instant publishedAt) {
    this(
        title,
        content,
        description,
        imageUrl,
        source,
        externalId,
        sourceUrl,
        category,
        publishedAt,
        NewsStatus.PUBLISHED);
  }

  private News(
      String title,
      String content,
      String description,
      String imageUrl,
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      Instant publishedAt,
      NewsStatus status) {
    this.title = title;
    this.content = content;
    this.description = description;
    this.imageUrl = imageUrl;
    this.source = source;
    this.externalId = externalId;
    this.sourceUrl = sourceUrl;
    this.category = category;
    this.publishedAt = publishedAt;
    this.status = status;
  }

  public static News createPublished(
      String title,
      String content,
      String description,
      String imageUrl,
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      Instant publishedAt) {
    return new News(
        title,
        content,
        description,
        imageUrl,
        source,
        externalId,
        sourceUrl,
        category,
        publishedAt,
        NewsStatus.PUBLISHED);
  }

  public static News createDraft(
      String title,
      String content,
      String description,
      String imageUrl,
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category) {
    return new News(
        title,
        content,
        description,
        imageUrl,
        source,
        externalId,
        sourceUrl,
        category,
        null,
        NewsStatus.DRAFT);
  }

  public static News createAdminDraft(
      String title,
      String content,
      String description,
      String imageUrl,
      String sourceUrl,
      Category category) {
    return new News(
        title,
        content,
        description,
        imageUrl,
        null,
        null,
        sourceUrl,
        category,
        null,
        NewsStatus.DRAFT);
  }

  public void update(
      String title,
      String content,
      String description,
      String imageUrl,
      String sourceUrl,
      Category category) {
    this.title = title;
    this.content = content;
    this.description = description;
    this.imageUrl = imageUrl;
    this.sourceUrl = sourceUrl;
    this.category = category;
  }

  public void publish() {
    this.status = NewsStatus.PUBLISHED;
    this.publishedAt = Instant.now();
  }

  public void reject() {
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
}
