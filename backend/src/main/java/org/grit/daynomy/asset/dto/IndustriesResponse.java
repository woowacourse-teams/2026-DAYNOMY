package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.asset.domain.Industry;

public record IndustriesResponse(
    @Schema(description = "노출 중인 업종 목록") List<IndustryResponse> industries) {

  public static IndustriesResponse from(List<Industry> industries) {
    return new IndustriesResponse(industries.stream().map(IndustryResponse::from).toList());
  }
}
