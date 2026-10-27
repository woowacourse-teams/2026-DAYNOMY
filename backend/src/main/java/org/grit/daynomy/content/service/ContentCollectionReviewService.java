package org.grit.daynomy.content.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionReviewStatus;
import org.grit.daynomy.content.dto.ContentCollectionRematchResponse;
import org.grit.daynomy.content.dto.ContentCollectionReviewBulkApproveRequest;
import org.grit.daynomy.content.dto.ContentCollectionReviewPageResponse;
import org.grit.daynomy.content.dto.ContentCollectionReviewResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ContentCollectionReviewService {

  private static final Set<AssetCategory> LINKABLE_CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);

  private final CollectedExternalContentRepository collectedContentRepository;
  private final CollectedExternalContentReviewRepository reviewRepository;
  private final AssetRepository assetRepository;
  private final AssetContentRepository assetContentRepository;
  private final AssetMentionMatcher assetMentionMatcher;

  @Transactional(readOnly = true)
  public ContentCollectionReviewPageResponse getPending(int page, int size) {
    var contents =
        collectedContentRepository.findAllWithReviewStatus(
            ContentCollectionReviewStatus.PENDING, PageRequest.of(page - 1, size));
    List<Long> contentIds = contents.stream().map(content -> content.getId()).toList();
    Map<Long, List<CollectedExternalContentReview>> reviewsByContent =
        contentIds.isEmpty()
            ? Map.of()
            : reviewRepository
                .findAllByCollectedContentIdInAndStatus(
                    contentIds, ContentCollectionReviewStatus.PENDING)
                .stream()
                .collect(Collectors.groupingBy(review -> review.getCollectedContent().getId()));
    List<ContentCollectionReviewResponse> items =
        contents.stream()
            .map(
                content ->
                    ContentCollectionReviewResponse.from(
                        content, reviewsByContent.getOrDefault(content.getId(), List.of())))
            .toList();
    return ContentCollectionReviewPageResponse.of(
        items,
        contents.getNumber() + 1,
        contents.getSize(),
        contents.getTotalPages(),
        contents.getTotalElements(),
        contents.hasNext());
  }

  @Transactional
  public void approve(Long contentId, List<Long> assetIds, LocalDate publishedDate) {
    approveContent(contentId, assetIds, publishedDate);
  }

  @Transactional
  public void approveAll(List<ContentCollectionReviewBulkApproveRequest.Item> items) {
    items.forEach(item -> approveContent(item.contentId(), item.assetIds(), item.publishedDate()));
  }

  @Transactional
  public void reject(Long contentId) {
    rejectContent(contentId);
  }

  @Transactional
  public void rejectAll(List<Long> contentIds) {
    new LinkedHashSet<>(contentIds).forEach(this::rejectContent);
  }

  @Transactional
  public ContentCollectionRematchResponse rematchPending() {
    List<CollectedExternalContentReview> unmatched =
        reviewRepository.findAllByStatusAndSuggestedAssetIsNullOrderByIdAsc(
            ContentCollectionReviewStatus.PENDING);
    List<Asset> assets = assetRepository.findAllByCategoryInAndListedTrue(LINKABLE_CATEGORIES);
    int matchedCount = 0;
    int suggestionCount = 0;
    Instant reviewedAt = Instant.now();
    for (CollectedExternalContentReview review : unmatched) {
      var content = review.getCollectedContent();
      List<Asset> matches =
          assetMentionMatcher.match(content.getTitle(), content.getDescription(), assets);
      if (matches.isEmpty()) {
        continue;
      }
      for (Asset asset : matches) {
        if (!reviewRepository.existsByCollectedContentIdAndSuggestedAssetId(
            content.getId(), asset.getId())) {
          reviewRepository.save(new CollectedExternalContentReview(content, asset));
          suggestionCount++;
        }
      }
      review.reject(reviewedAt);
      matchedCount++;
    }
    return new ContentCollectionRematchResponse(unmatched.size(), matchedCount, suggestionCount);
  }

  private void approveContent(
      Long contentId, List<Long> requestedAssetIds, LocalDate publishedDate) {
    List<CollectedExternalContentReview> reviews = getPending(contentId);
    List<Long> assetIds = new LinkedHashSet<>(requestedAssetIds).stream().toList();
    Map<Long, Asset> assetsById =
        assetRepository.findAllById(assetIds).stream()
            .collect(Collectors.toMap(Asset::getId, Function.identity()));
    if (assetsById.size() != assetIds.size()) {
      throw new BusinessException(AssetErrorCode.ASSET_NOT_FOUND);
    }
    var content = reviews.getFirst().getCollectedContent();
    content.confirmPublishedDate(publishedDate);
    for (Long assetId : assetIds) {
      Asset asset = assetsById.get(assetId);
      if (!assetContentRepository.existsByAssetIdAndUrl(assetId, content.getUrl())) {
        assetContentRepository.save(
            AssetContent.create(
                asset,
                content.getSource().getSourceType(),
                content.getTitle(),
                content.getUrl(),
                content.getImageUrl(),
                content.getPublishedDate()));
      }
    }

    Set<Long> selected = Set.copyOf(assetIds);
    boolean hasSelectedSuggestion =
        reviews.stream()
            .map(CollectedExternalContentReview::getSuggestedAsset)
            .anyMatch(asset -> asset != null && selected.contains(asset.getId()));
    Instant reviewedAt = Instant.now();
    for (int index = 0; index < reviews.size(); index++) {
      CollectedExternalContentReview review = reviews.get(index);
      Asset suggestion = review.getSuggestedAsset();
      if ((suggestion != null && selected.contains(suggestion.getId()))
          || (!hasSelectedSuggestion && index == 0)) {
        review.approve(
            suggestion == null ? assetsById.get(assetIds.getFirst()) : suggestion, reviewedAt);
      } else {
        review.reject(reviewedAt);
      }
    }
  }

  private void rejectContent(Long contentId) {
    Instant reviewedAt = Instant.now();
    getPending(contentId).forEach(review -> review.reject(reviewedAt));
  }

  private List<CollectedExternalContentReview> getPending(Long contentId) {
    List<CollectedExternalContentReview> reviews =
        reviewRepository.findAllByCollectedContentIdAndStatusOrderByIdAsc(
            contentId, ContentCollectionReviewStatus.PENDING);
    if (reviews.isEmpty()) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_REVIEW_NOT_FOUND);
    }
    return reviews;
  }
}
