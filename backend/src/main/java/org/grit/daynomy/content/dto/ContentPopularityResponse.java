package org.grit.daynomy.content.dto;

import java.util.List;

public record ContentPopularityResponse(List<ContentPopularityItemResponse> items, int periodDays) {
  public ContentPopularityResponse {
    items = List.copyOf(items);
  }
}
