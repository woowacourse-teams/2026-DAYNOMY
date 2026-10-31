package org.grit.daynomy.content.dto;

import org.grit.daynomy.asset.domain.Asset;

public record ContentCollectionReviewAssetResponse(Long id, String name, String assetCode) {

  public static ContentCollectionReviewAssetResponse from(Asset asset) {
    return new ContentCollectionReviewAssetResponse(
        asset.getId(), asset.getDisplayName(), asset.getAssetCode());
  }
}
