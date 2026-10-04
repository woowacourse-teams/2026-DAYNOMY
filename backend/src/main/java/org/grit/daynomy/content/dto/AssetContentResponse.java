package org.grit.daynomy.content.dto;

import java.time.Instant;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;

public record AssetContentResponse(
    Long id,
    Long assetId,
    ContentSourceType sourceType,
    String title,
    String url,
    Instant createdAt) {

  public static AssetContentResponse from(AssetContent content) {
    return new AssetContentResponse(
        content.getId(),
        content.getAsset().getId(),
        content.getSourceType(),
        content.getTitle(),
        content.getUrl(),
        content.getCreatedAt());
  }
}
