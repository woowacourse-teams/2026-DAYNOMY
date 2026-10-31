package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;

public record ContentCollectionSourceResponse(
    Long id,
    String name,
    ContentSourceType sourceType,
    String sourceUrl,
    Long assetId,
    String assetName,
    boolean enabled,
    @Schema(description = "마지막 성공 수집 시각") Instant lastCollectedAt) {

  public static ContentCollectionSourceResponse from(ContentCollectionSource source) {
    Asset asset = source.getAsset();
    return new ContentCollectionSourceResponse(
        source.getId(),
        source.getName(),
        source.getSourceType(),
        source.getSourceUrl(),
        asset == null ? null : asset.getId(),
        asset == null ? null : asset.getDisplayName(),
        source.isEnabled(),
        source.getLastCollectedAt());
  }
}
