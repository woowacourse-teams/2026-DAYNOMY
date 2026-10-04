package org.grit.daynomy.content.service;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.news.domain.News;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AssetContentService {

  private final AssetRepository assetRepository;
  private final AssetContentRepository contentRepository;

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
        AssetContent.create(asset, request.sourceType(), request.title().strip(), url);
    return AssetContentResponse.from(contentRepository.save(content));
  }

  @Transactional
  public void deleteContent(Long assetId, Long contentId) {
    getAsset(assetId);
    AssetContent content =
        contentRepository
            .findByIdAndAsset_Id(contentId, assetId)
            .orElseThrow(
                () -> new BusinessException(ContentErrorCode.ASSET_CONTENT_NOT_FOUND));
    contentRepository.delete(content);
  }

  private Asset getAsset(Long assetId) {
    return assetRepository
        .findById(assetId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
  }
}
