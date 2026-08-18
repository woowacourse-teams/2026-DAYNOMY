package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;

public record NewsDetailResponse(
    @Schema(description = "뉴스 ID", example = "1") Long id,
    @Schema(description = "뉴스 제목", example = "뉴스 제목") String title,
    @Schema(description = "뉴스 본문", example = "뉴스 본문") String content,
    @Schema(description = "뉴스 요약 설명", example = "뉴스 요약") String description,
    @Schema(description = "뉴스 이미지 URL", example = "https://example.com/news.png") String imageUrl,
    @Schema(description = "뉴스 카테고리", example = "STOCK") Category category,
    @Schema(description = "발행 시각", example = "2026-08-17T10:00:00") LocalDateTime publishedAt) {

  public static NewsDetailResponse from(News news) {
    return new NewsDetailResponse(
        news.getId(),
        news.getTitle(),
        news.getContent(),
        news.getDescription(),
        news.getImageUrl(),
        news.getCategory(),
        news.getPublishedAt());
  }
}
