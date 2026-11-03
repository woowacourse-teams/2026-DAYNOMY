package org.grit.daynomy.content.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.dto.ContentCollectionSourceRequest;
import org.grit.daynomy.content.dto.ContentCollectionSourceResponse;
import org.grit.daynomy.content.dto.ContentCollectionSourcesResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ContentCollectionSourceService {

  private final ContentCollectionSourceRepository sourceRepository;
  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public ContentCollectionSourcesResponse getSources() {
    return new ContentCollectionSourcesResponse(
        sourceRepository.findAllByOrderByIdAsc().stream()
            .map(ContentCollectionSourceResponse::from)
            .toList());
  }

  @Transactional
  public ContentCollectionSourceResponse create(ContentCollectionSourceRequest request) {
    String sourceUrl =
        ContentCollectionSourceUrl.normalize(request.sourceType(), request.sourceUrl());
    if (sourceRepository.existsBySourceTypeAndSourceUrl(request.sourceType(), sourceUrl)) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_ALREADY_EXISTS);
    }
    ContentCollectionSource source =
        new ContentCollectionSource(
            request.name().strip(),
            request.sourceType(),
            sourceUrl,
            findAsset(request.assetId()),
            request.enabled());
    return ContentCollectionSourceResponse.from(sourceRepository.save(source));
  }

  @Transactional
  public ContentCollectionSourceResponse update(
      Long sourceId, ContentCollectionSourceRequest request) {
    ContentCollectionSource source = findSource(sourceId);
    String sourceUrl =
        ContentCollectionSourceUrl.normalize(request.sourceType(), request.sourceUrl());
    if (sourceRepository.existsBySourceTypeAndSourceUrlAndIdNot(
        request.sourceType(), sourceUrl, sourceId)) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_ALREADY_EXISTS);
    }
    source.update(
        request.name().strip(),
        request.sourceType(),
        sourceUrl,
        findAsset(request.assetId()),
        request.enabled());
    return ContentCollectionSourceResponse.from(source);
  }

  private ContentCollectionSource findSource(Long sourceId) {
    return sourceRepository
        .findById(sourceId)
        .orElseThrow(
            () -> new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));
  }

  private Asset findAsset(Long assetId) {
    if (assetId == null) {
      return null;
    }
    return assetRepository
        .findById(assetId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
  }
}
