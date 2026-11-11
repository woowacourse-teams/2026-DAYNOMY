package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.asset.domain.Industry;

public record IndustryResponse(
    @Schema(description = "업종 코드", example = "SEMICONDUCTOR") String code,
    @Schema(description = "업종명", example = "반도체") String name) {

  public static IndustryResponse from(Industry industry) {
    return new IndustryResponse(industry.getCode(), industry.getName());
  }
}
