package org.grit.daynomy.content.dto;

import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.content.domain.AssetContent;

public record AssetContentsResponse(
    Long assetId, String assetCode, String assetName, List<AssetContentResponse> contents) {

  public static AssetContentsResponse from(Asset asset, List<AssetContent> contents) {
    return new AssetContentsResponse(
        asset.getId(),
        asset.getAssetCode(),
        asset.getName(),
        contents.stream().map(AssetContentResponse::from).toList());
  }
}
