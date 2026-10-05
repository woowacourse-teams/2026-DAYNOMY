package org.grit.daynomy.content.dto;

import java.util.List;

public record YouTubeSearchResponse(List<YouTubeVideoResponse> items) {

  public YouTubeSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
