package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record WikimediaImageSearchResponse(
    @Schema(description = "검색된 Wikimedia Commons 이미지 목록")
        List<WikimediaImageCandidateResponse> items) {

  public WikimediaImageSearchResponse {
    items = List.copyOf(items);
  }
}
