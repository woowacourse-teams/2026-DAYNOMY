package org.grit.daynomy.content.service;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.dto.YouTubeSearchResponse;
import org.grit.daynomy.content.dto.YouTubeVideoResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.news.domain.News;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AssetContentService {

  private static final String ASSET_CONTENT_URL_UNIQUE_CONSTRAINT =
      "uk_stock_related_contents_asset_url";

  private final AssetRepository assetRepository;
  private final AssetContentRepository contentRepository;
  private final YouTubeClient youtubeClient;

  public AssetContentsResponse getContents(Long assetId) {
    Asset asset = getAsset(assetId);
    return AssetContentsResponse.from(
        asset,
        contentRepository.findAllByAssetIdOrderByCreatedAtDescIdDesc(assetId).stream()
            .filter(content -> content.getNews() == null || content.getNews().isPublished())
            .toList());
  }

  public List<StockSearchItemResponse> getRelatedAssets(Long newsId) {
    return contentRepository.findAllByNewsId(newsId).stream()
        .map(AssetContent::getAsset)
        .map(StockSearchItemResponse::from)
        .toList();
  }

  public YouTubeSearchResponse searchYouTube(Long assetId, String keyword) {
    getAsset(assetId);
    return new YouTubeSearchResponse(
        youtubeClient.search(keyword).stream().map(YouTubeVideoResponse::from).toList());
  }

  @Transactional
  public void syncNewsContents(News news, List<Long> assetIds) {
    if (news.getId() == null) {
      throw new IllegalStateException("뉴스가 저장된 후 관련 종목을 연결해야 합니다.");
    }

    List<Long> distinctAssetIds =
        new LinkedHashSet<>(assetIds == null ? List.of() : assetIds).stream().toList();
    List<Asset> assets = assetRepository.findAllById(distinctAssetIds);
    if (assets.size() != distinctAssetIds.size()) {
      throw new BusinessException(AssetErrorCode.ASSET_NOT_FOUND);
    }

    contentRepository.deleteAllByNewsId(news.getId());
    contentRepository.flush();
    contentRepository.saveAll(
        assets.stream().map(asset -> AssetContent.createInternalNews(asset, news)).toList());
  }

  @Transactional
  public AssetContentResponse createContent(Long assetId, AssetContentRequest request) {
    Asset asset = getAsset(assetId);
    String url = request.url().strip();
    if (contentRepository.existsByAssetIdAndUrl(assetId, url)) {
      throw new BusinessException(ContentErrorCode.ASSET_CONTENT_ALREADY_EXISTS);
    }

    AssetContent content =
        AssetContent.create(
            asset,
            request.sourceType(),
            request.title().strip(),
            url,
            stripToNull(request.imageUrl()));
    try {
      return AssetContentResponse.from(contentRepository.saveAndFlush(content));
    } catch (DataIntegrityViolationException exception) {
      if (isAssetContentUrlUniqueConstraintViolation(exception)) {
        throw new BusinessException(ContentErrorCode.ASSET_CONTENT_ALREADY_EXISTS);
      }
      throw exception;
    }
  }

  @Transactional
  public void deleteContent(Long assetId, Long contentId) {
    getAsset(assetId);
    AssetContent content =
        contentRepository
            .findByIdAndAsset_Id(contentId, assetId)
            .orElseThrow(() -> new BusinessException(ContentErrorCode.ASSET_CONTENT_NOT_FOUND));
    contentRepository.delete(content);
  }

  private Asset getAsset(Long assetId) {
    return assetRepository
        .findById(assetId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
  }

  private String stripToNull(String value) {
    if (value == null) return null;
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private boolean isAssetContentUrlUniqueConstraintViolation(
      DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolationException) {
        return ASSET_CONTENT_URL_UNIQUE_CONSTRAINT.equals(
            constraintViolationException.getConstraintName());
      }
      cause = cause.getCause();
    }
    return false;
  }
}
