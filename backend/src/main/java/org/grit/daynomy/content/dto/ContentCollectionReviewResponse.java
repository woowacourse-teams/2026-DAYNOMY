package org.grit.daynomy.content.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentSourceType;

public record ContentCollectionReviewResponse(
    Long id,
    String sourceName,
    ContentSourceType sourceType,
    String title,
    String description,
    String url,
    String imageUrl,
    LocalDate publishedDate,
    List<ContentCollectionReviewAssetResponse> suggestedAssets,
    Instant createdAt) {

  public static ContentCollectionReviewResponse from(
      CollectedExternalContent content, List<CollectedExternalContentReview> reviews) {
    return new ContentCollectionReviewResponse(
        content.getId(),
        content.getSource().getName(),
        content.getSource().getSourceType(),
        content.getTitle(),
        content.getDescription(),
        content.getUrl(),
        content.getImageUrl(),
        content.getPublishedDate(),
        reviews.stream()
            .map(CollectedExternalContentReview::getSuggestedAsset)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(ContentCollectionReviewAssetResponse::from)
            .toList(),
        content.getCreatedAt());
  }
}
