package org.grit.daynomy.asset.dto;

import java.util.List;
import org.grit.daynomy.asset.domain.Industry;

public record AdminIndustriesResponse(List<AdminIndustryResponse> industries) {

  public static AdminIndustriesResponse from(List<Industry> industries) {
    return new AdminIndustriesResponse(
        industries.stream().map(AdminIndustryResponse::from).toList());
  }
}
