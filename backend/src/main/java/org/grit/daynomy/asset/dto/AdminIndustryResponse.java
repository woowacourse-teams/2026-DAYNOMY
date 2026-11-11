package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.asset.domain.Industry;

public record AdminIndustryResponse(
    @Schema(description = "업종 ID") Long id,
    @Schema(description = "변경할 수 없는 업종 코드", example = "SEMICONDUCTOR") String code,
    @Schema(description = "업종명", example = "반도체") String name,
    @Schema(description = "노출 순서", example = "1") int displayOrder,
    @Schema(description = "활성 여부") boolean active) {

  public static AdminIndustryResponse from(Industry industry) {
    return new AdminIndustryResponse(
        industry.getId(),
        industry.getCode(),
        industry.getName(),
        industry.getDisplayOrder(),
        industry.isActive());
  }
}
