package org.grit.daynomy.search.dto;

import java.util.List;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.springframework.data.domain.Page;

public record NewsSearchResponse(
    List<NewsListItemResponse> content, int page, int size, long totalElements, int totalPages) {

  public static NewsSearchResponse from(Page<News> news) {
    return new NewsSearchResponse(
        news.getContent().stream().map(NewsListItemResponse::from).toList(),
        news.getNumber(),
        news.getSize(),
        news.getTotalElements(),
        news.getTotalPages());
  }
}
