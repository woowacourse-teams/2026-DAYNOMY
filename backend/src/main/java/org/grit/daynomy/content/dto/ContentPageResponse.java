package org.grit.daynomy.content.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ContentPageResponse(
    List<AssetContentResponse> items,
    int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {
  public static ContentPageResponse from(Page<AssetContentResponse> page) {
    return new ContentPageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
