package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;

public record AdminNewsResponse(
    @Schema(description = "뉴스 ID", example = "1") Long id,
    @Schema(description = "뉴스 제목", example = "뉴스 제목") String title,
    @Schema(description = "뉴스 본문", example = "뉴스 본문") String content,
    @Schema(description = "뉴스 이미지 URL", example = "https://example.com/news.png") String imageUrl,
    @Schema(description = "뉴스 이미지 출처") ImageSourceResponse imageSource,
    @Schema(description = "뉴스 출처 목록") List<NewsSourceResponse> sources,
    @Schema(description = "뉴스 카테고리", example = "STOCK") Category category,
    @Schema(description = "발행 시각", example = "2026-08-17T10:00:00Z") Instant publishedAt,
    @Schema(description = "뉴스 상태", example = "DRAFT") NewsStatus status,
    @Schema(description = "관련 종목 목록") List<StockSearchItemResponse> relatedAssets) {

  public static AdminNewsResponse from(News news) {
    return from(news, List.of());
  }

  public static AdminNewsResponse from(News news, List<StockSearchItemResponse> relatedAssets) {
    return new AdminNewsResponse(
        news.getId(),
        news.getTitle(),
        news.getContent(),
        news.getImageUrl(),
        ImageSourceResponse.from(news.getImageSource()),
        NewsSourceResponse.from(news),
        news.getCategory(),
        news.getPublishedAt(),
        news.getStatus(),
        relatedAssets == null ? List.of() : List.copyOf(relatedAssets));
  }
}
