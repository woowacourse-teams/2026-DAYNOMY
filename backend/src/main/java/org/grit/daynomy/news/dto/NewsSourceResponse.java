package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record NewsSourceResponse(
    @Schema(description = "출처명", example = "DART 전자공시시스템") String name,
    @Schema(description = "출처 URL", example = "https://example.com/news/1") String url) {

  public static NewsSourceResponse from(NewsSourceInfo source) {
    return new NewsSourceResponse(source.name(), source.url());
  }

  public static List<NewsSourceResponse> from(News news) {
    return news.getSources().stream().map(NewsSourceResponse::from).toList();
  }
}
