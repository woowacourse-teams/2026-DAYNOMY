package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;

public record FeaturedPersonPostResponse(
    @Schema(description = "게시물 ID", example = "1") Long id,
    @Schema(description = "게시물 출처", example = "X") ContentSourceType sourceType,
    @Schema(description = "게시물 제목", example = "시장 전망에 대한 의견") String title,
    @Schema(description = "게시물 URL", example = "https://x.com/example/status/1") String url,
    @Schema(description = "원문 게시 시각", example = "2026-10-22T09:00:00Z") Instant publishedAt,
    @Schema(description = "등록 시각", example = "2026-10-23T09:00:00Z") Instant createdAt) {

  public static FeaturedPersonPostResponse from(FeaturedPersonPost post) {
    return new FeaturedPersonPostResponse(
        post.getId(),
        post.getSourceType(),
        post.getTitle(),
        post.getUrl(),
        post.getPublishedAt(),
        post.getCreatedAt());
  }
}
