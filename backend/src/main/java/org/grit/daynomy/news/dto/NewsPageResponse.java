package org.grit.daynomy.news.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record NewsPageResponse(
    List<NewsListItemResponse> items,
    int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {

  public static NewsPageResponse from(Page<NewsListItemResponse> page) {
    return new NewsPageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
