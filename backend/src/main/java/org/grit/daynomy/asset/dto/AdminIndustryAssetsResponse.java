package org.grit.daynomy.asset.dto;

import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.springframework.data.domain.Page;

public record AdminIndustryAssetsResponse(
    Long industryId,
    List<StockSearchItemResponse> items,
    int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {

  public static AdminIndustryAssetsResponse from(Long industryId, Page<Asset> assets) {
    return new AdminIndustryAssetsResponse(
        industryId,
        assets.getContent().stream().map(StockSearchItemResponse::from).toList(),
        assets.getNumber() + 1,
        assets.getSize(),
        assets.getTotalPages(),
        assets.getTotalElements(),
        assets.hasNext());
  }
}
