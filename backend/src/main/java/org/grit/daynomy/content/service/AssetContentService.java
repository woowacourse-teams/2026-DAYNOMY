package org.grit.daynomy.content.service;

import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.dto.ContentPageResponse;
import org.grit.daynomy.content.dto.SocialPostResponse;
import org.grit.daynomy.content.dto.SocialPostSearchResponse;
import org.grit.daynomy.content.dto.YouTubeSearchResponse;
import org.grit.daynomy.content.dto.YouTubeVideoResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.news.domain.News;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@Service
public class AssetContentService {

  private static final String ASSET_CONTENT_URL_UNIQUE_CONSTRAINT =
      "uk_stock_related_contents_asset_url";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final AssetRepository assetRepository;
  private final AssetContentRepository contentRepository;
  private final YouTubeClient youtubeClient;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;

  public ContentPageResponse getPublicContents(int page, int size) {
    return ContentPageResponse.from(
        contentRepository
            .findAllBySourceTypeInAndNewsIsNullOrderByPublishedDateDescCreatedAtDescIdDesc(
                List.of(ContentSourceType.YOUTUBE, ContentSourceType.THREADS, ContentSourceType.X),
                PageRequest.of(page - 1, size))
            .map(AssetContentResponse::from));
  }

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

  public SocialPostSearchResponse searchSocialPosts(Long assetId, String keyword) {
    getAsset(assetId);
    return new SocialPostSearchResponse(
        socialPostSearchClient.search(keyword).stream().map(SocialPostResponse::from).toList());
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
    log.atInfo()
        .addKeyValue("event", LogEvent.ASSET_CONTENT_SYNC_COMPLETED.code())
        .addKeyValue("newsId", news.getId())
        .addKeyValue("assetCount", assets.size())
        .log(LogEvent.ASSET_CONTENT_SYNC_COMPLETED.message());
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
            stripToNull(request.imageUrl()),
            request.publishedDate());
    try {
      AssetContent saved = contentRepository.saveAndFlush(content);
      if (saved.getPublishedDate() == null) {
        saved.fillPublishedDateIfMissing(saved.getCreatedAt().atZone(SEOUL).toLocalDate());
      }
      log.atInfo()
          .addKeyValue("event", LogEvent.ASSET_CONTENT_CREATED.code())
          .addKeyValue("assetId", assetId)
          .addKeyValue("contentId", saved.getId())
          .addKeyValue("sourceType", saved.getSourceType())
          .log(LogEvent.ASSET_CONTENT_CREATED.message());
      return AssetContentResponse.from(saved);
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
    log.atInfo()
        .addKeyValue("event", LogEvent.ASSET_CONTENT_DELETED.code())
        .addKeyValue("assetId", assetId)
        .addKeyValue("contentId", contentId)
        .addKeyValue("sourceType", content.getSourceType())
        .log(LogEvent.ASSET_CONTENT_DELETED.message());
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
