package org.grit.daynomy.content.dto;

import java.util.List;

public record ContentCollectionReviewPageResponse(
    List<ContentCollectionReviewResponse> items,
    int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {

  public static ContentCollectionReviewPageResponse of(
      List<ContentCollectionReviewResponse> items,
      int page,
      int size,
      int totalPages,
      long totalElements,
      boolean hasNext) {
    return new ContentCollectionReviewPageResponse(
        items, page, size, totalPages, totalElements, hasNext);
  }
}
