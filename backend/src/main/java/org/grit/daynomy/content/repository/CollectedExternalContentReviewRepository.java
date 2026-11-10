package org.grit.daynomy.content.repository;

import java.util.Collection;
import java.util.List;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionReviewStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectedExternalContentReviewRepository
    extends JpaRepository<CollectedExternalContentReview, Long> {

  boolean existsByCollectedContentIdAndSuggestedAssetId(Long contentId, Long assetId);

  boolean existsByCollectedContentIdAndSuggestedAssetIsNull(Long contentId);

  @EntityGraph(attributePaths = {"collectedContent", "collectedContent.source", "suggestedAsset"})
  List<CollectedExternalContentReview> findAllByCollectedContentIdInAndStatus(
      Collection<Long> contentIds, ContentCollectionReviewStatus status);

  @EntityGraph(attributePaths = {"collectedContent", "collectedContent.source", "suggestedAsset"})
  List<CollectedExternalContentReview> findAllByCollectedContentIdAndStatusOrderByIdAsc(
      Long contentId, ContentCollectionReviewStatus status);

  @EntityGraph(attributePaths = {"collectedContent", "collectedContent.source"})
  List<CollectedExternalContentReview> findAllByStatusAndSuggestedAssetIsNullOrderByIdAsc(
      ContentCollectionReviewStatus status);
}
