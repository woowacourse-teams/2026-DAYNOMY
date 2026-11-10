package org.grit.daynomy.news.dto;

import java.util.List;
import org.grit.daynomy.search.dto.ContentSearchResponse;

public record NewsFeedResponse(
    List<NewsFeedItemResponse> items, int page, int size, long totalElements, int totalPages) {

  public static NewsFeedResponse from(ContentSearchResponse response) {
    return new NewsFeedResponse(
        response.items().stream().map(NewsFeedItemResponse::from).toList(),
        response.page(),
        response.size(),
        response.totalElements(),
        response.totalPages());
  }
}
