package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SocialPostSearchResponse(
    @Schema(description = "검색된 소셜 게시물 목록") List<SocialPostResponse> items) {

  public SocialPostSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
