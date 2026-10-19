package org.grit.daynomy.person.domain;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "featured_person_posts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_featured_person_posts_person_url",
            columnNames = {"person_id", "url"}))
public class FeaturedPersonPost extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "person_id", nullable = false)
  private FeaturedPerson person;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private ContentSourceType sourceType;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "url", nullable = false, columnDefinition = "TEXT")
  private String url;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FeaturedPersonPostStatus status;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  private FeaturedPersonPost(
      FeaturedPerson person,
      ContentSourceType sourceType,
      String title,
      String url,
      Instant publishedAt,
      FeaturedPersonPostStatus status) {
    this.person = person;
    this.sourceType = sourceType;
    this.title = title;
    this.url = url;
    this.publishedAt = publishedAt;
    this.status = status;
  }

  public static FeaturedPersonPost create(
      FeaturedPerson person,
      ContentSourceType sourceType,
      String title,
      String url,
      Instant publishedAt) {
    return new FeaturedPersonPost(
        person, sourceType, title, url, publishedAt, FeaturedPersonPostStatus.APPROVED);
  }

  public static FeaturedPersonPost createPending(
      FeaturedPerson person,
      ContentSourceType sourceType,
      String title,
      String url,
      Instant publishedAt) {
    return new FeaturedPersonPost(
        person, sourceType, title, url, publishedAt, FeaturedPersonPostStatus.PENDING);
  }

  public void approve(Instant reviewedAt) {
    requirePending();
    status = FeaturedPersonPostStatus.APPROVED;
    this.reviewedAt = reviewedAt;
  }

  public void reject(Instant reviewedAt) {
    requirePending();
    status = FeaturedPersonPostStatus.REJECTED;
    this.reviewedAt = reviewedAt;
  }

  private void requirePending() {
    if (status != FeaturedPersonPostStatus.PENDING) {
      throw new BusinessException(
          FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING);
    }
  }
}
