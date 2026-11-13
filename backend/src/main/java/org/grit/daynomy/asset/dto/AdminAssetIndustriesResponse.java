package org.grit.daynomy.asset.dto;

import java.util.List;
import org.grit.daynomy.asset.domain.Industry;

public record AdminAssetIndustriesResponse(Long assetId, List<AdminIndustryResponse> industries) {

  public static AdminAssetIndustriesResponse from(Long assetId, List<Industry> industries) {
    return new AdminAssetIndustriesResponse(
        assetId, industries.stream().map(AdminIndustryResponse::from).toList());
  }
}
