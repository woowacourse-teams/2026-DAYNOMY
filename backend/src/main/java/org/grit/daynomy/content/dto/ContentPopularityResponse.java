package org.grit.daynomy.content.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ContentPopularityResponse(
    List<ContentPopularityItemResponse> items,
    int periodDays,
    int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {
  public ContentPopularityResponse {
    items = List.copyOf(items);
  }

  public static ContentPopularityResponse from(
      Page<ContentPopularityItemResponse> popularity, int periodDays) {
    return new ContentPopularityResponse(
        popularity.getContent(),
        periodDays,
        popularity.getNumber() + 1,
        popularity.getSize(),
        popularity.getTotalPages(),
        popularity.getTotalElements(),
        popularity.hasNext());
  }
}
