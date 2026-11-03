package org.grit.daynomy.content.domain;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.exception.ContentErrorCode;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collected_external_content_reviews")
public class CollectedExternalContentReview extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "collected_content_id", nullable = false)
  private CollectedExternalContent collectedContent;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "suggested_asset_id")
  private Asset suggestedAsset;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContentCollectionReviewStatus status;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  public CollectedExternalContentReview(
      CollectedExternalContent collectedContent, Asset suggestedAsset) {
    this.collectedContent = collectedContent;
    this.suggestedAsset = suggestedAsset;
    this.status = ContentCollectionReviewStatus.PENDING;
  }

  public void approve(Asset asset, Instant reviewedAt) {
    requirePending();
    this.suggestedAsset = asset;
    this.status = ContentCollectionReviewStatus.APPROVED;
    this.reviewedAt = reviewedAt;
  }

  public void reject(Instant reviewedAt) {
    requirePending();
    this.status = ContentCollectionReviewStatus.REJECTED;
    this.reviewedAt = reviewedAt;
  }

  private void requirePending() {
    if (status != ContentCollectionReviewStatus.PENDING) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_REVIEW_NOT_PENDING);
    }
  }
}
