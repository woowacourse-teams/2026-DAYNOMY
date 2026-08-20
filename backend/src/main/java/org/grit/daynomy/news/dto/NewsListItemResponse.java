package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;

public record NewsListItemResponse(
    @Schema(description = "뉴스 ID", example = "1") Long id,
    @Schema(description = "뉴스 제목", example = "뉴스 제목") String title,
    @Schema(description = "뉴스 요약 설명", example = "뉴스 요약") String description,
    @Schema(description = "뉴스 이미지 URL", example = "https://example.com/news.png") String imageUrl,
    @Schema(description = "뉴스 출처", example = "DART") NewsSource source,
    @Schema(description = "원문 URL", example = "https://example.com/news/1") String sourceUrl,
    @Schema(description = "뉴스 카테고리", example = "STOCK") Category category,
    @Schema(description = "발행 시각", example = "2026-08-17T10:00:00Z") Instant publishedAt) {

  public NewsListItemResponse(
      Long id,
      String title,
      String description,
      String imageUrl,
      Category category,
      Instant publishedAt) {
    this(id, title, description, imageUrl, null, null, category, publishedAt);
  }

  public static NewsListItemResponse from(News news) {
    return new NewsListItemResponse(
        news.getId(),
        news.getTitle(),
        news.getDescription(),
        news.getImageUrl(),
        news.getSource(),
        news.getSourceUrl(),
        news.getCategory(),
        news.getPublishedAt());
  }
}
