package org.grit.daynomy.content.service;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.CollectedExternalContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentReviewRepository;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ContentCollectionPersistenceService {

  private static final Set<AssetCategory> LINKABLE_CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);

  private final ContentCollectionSourceRepository sourceRepository;
  private final CollectedExternalContentRepository collectedContentRepository;
  private final CollectedExternalContentReviewRepository reviewRepository;
  private final AssetRepository assetRepository;
  private final AssetMentionMatcher assetMentionMatcher;

  @Transactional
  public ContentCollectionResultResponse persist(
      Long sourceId, List<CollectedContentItem> items, java.time.Instant collectedAt) {
    ContentCollectionSource source = getSource(sourceId);
    List<Asset> matchableAssets = matchableAssets(source);
    int savedCount = 0;
    int duplicateCount = 0;
    int linkedCount = 0;
    for (CollectedContentItem item : items) {
      CollectedExternalContent collectedContent =
          collectedContentRepository.findByUrl(item.url()).orElse(null);
      if (collectedContent != null) {
        duplicateCount++;
      } else {
        collectedContent =
            collectedContentRepository.save(
                new CollectedExternalContent(
                    source,
                    fit(item.title(), 255),
                    item.description(),
                    item.url(),
                    item.imageUrl(),
                    item.publishedDate()));
        savedCount++;
      }
      List<Asset> targets =
          source.getAsset() == null
              ? assetMentionMatcher.match(item.title(), item.description(), matchableAssets)
              : List.of(source.getAsset());
      for (Asset asset : targets) {
        if (!reviewRepository.existsByCollectedContentIdAndSuggestedAssetId(
            collectedContent.getId(), asset.getId())) {
          reviewRepository.save(new CollectedExternalContentReview(collectedContent, asset));
          linkedCount++;
        }
      }
      if (targets.isEmpty()
          && !reviewRepository.existsByCollectedContentIdAndSuggestedAssetIsNull(
              collectedContent.getId())) {
        reviewRepository.save(new CollectedExternalContentReview(collectedContent, null));
      }
    }
    source.completeCollection(collectedAt);
    return new ContentCollectionResultResponse(
        sourceId, items.size(), savedCount, duplicateCount, linkedCount);
  }

  @Transactional
  public ContentCollectionResultResponse persistManual(Long sourceId, CollectedContentItem item) {
    ContentCollectionSource source = getSource(sourceId);
    if (collectedContentRepository.findByUrl(item.url()).isPresent()) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_CONTENT_ALREADY_EXISTS);
    }
    CollectedExternalContent collectedContent =
        collectedContentRepository.save(
            new CollectedExternalContent(
                source,
                fit(item.title(), 255),
                item.description(),
                item.url(),
                item.imageUrl(),
                item.publishedDate()));
    List<Asset> targets = targets(source, item, matchableAssets(source));
    int linkedCount = createReviews(collectedContent, targets);
    return new ContentCollectionResultResponse(sourceId, 1, 1, 0, linkedCount);
  }

  private ContentCollectionSource getSource(Long sourceId) {
    return sourceRepository
        .findById(sourceId)
        .orElseThrow(
            () -> new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));
  }

  private List<Asset> matchableAssets(ContentCollectionSource source) {
    return source.getAsset() == null
        ? assetRepository.findAllByCategoryInAndListedTrue(LINKABLE_CATEGORIES)
        : List.of();
  }

  private List<Asset> targets(
      ContentCollectionSource source, CollectedContentItem item, List<Asset> matchableAssets) {
    return source.getAsset() == null
        ? assetMentionMatcher.match(item.title(), item.description(), matchableAssets)
        : List.of(source.getAsset());
  }

  private int createReviews(CollectedExternalContent collectedContent, List<Asset> targets) {
    int linkedCount = 0;
    for (Asset asset : targets) {
      if (!reviewRepository.existsByCollectedContentIdAndSuggestedAssetId(
          collectedContent.getId(), asset.getId())) {
        reviewRepository.save(new CollectedExternalContentReview(collectedContent, asset));
        linkedCount++;
      }
    }
    if (targets.isEmpty()
        && !reviewRepository.existsByCollectedContentIdAndSuggestedAssetIsNull(
            collectedContent.getId())) {
      reviewRepository.save(new CollectedExternalContentReview(collectedContent, null));
    }
    return linkedCount;
  }

  private String fit(String value, int maxLength) {
    String stripped = value == null ? "" : value.strip();
    return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
  }
}
