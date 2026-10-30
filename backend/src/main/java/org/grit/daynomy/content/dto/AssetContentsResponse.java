package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.content.domain.AssetContent;

public record AssetContentsResponse(
    @Schema(description = "자산 ID", example = "1") Long assetId,
    @Schema(description = "자산 코드", example = "005930") String assetCode,
    @Schema(description = "자산 이름", example = "삼성전자") String assetName,
    @Schema(description = "관련 자료 목록") List<AssetContentResponse> contents) {

  public static AssetContentsResponse from(Asset asset, List<AssetContent> contents) {
    return new AssetContentsResponse(
        asset.getId(),
        asset.getAssetCode(),
        asset.getDisplayName(),
        contents.stream().map(AssetContentResponse::from).toList());
  }
}
