package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record YouTubeSearchResponse(
    @Schema(description = "검색된 YouTube 영상 목록") List<YouTubeVideoResponse> items) {

  public YouTubeSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
