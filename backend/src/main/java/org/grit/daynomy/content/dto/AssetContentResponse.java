package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;

public record AssetContentResponse(
    @Schema(description = "관련 자료 ID", example = "1") Long id,
    @Schema(description = "자산 ID", example = "10") Long assetId,
    @Schema(description = "콘텐츠 출처", example = "YOUTUBE") ContentSourceType sourceType,
    @Schema(description = "콘텐츠 제목", example = "삼성전자 3분기 실적 분석") String title,
    @Schema(description = "원문 URL", example = "https://example.com/contents/1") String url,
    @Schema(description = "대표 이미지 URL", example = "https://example.com/images/1.png")
        String imageUrl,
    @Schema(description = "등록 시각", example = "2026-10-23T09:00:00Z") Instant createdAt,
    @Schema(description = "원문 게시일", example = "2026-10-22") LocalDate publishedDate) {

  public static AssetContentResponse from(AssetContent content) {
    return new AssetContentResponse(
        content.getId(),
        content.getAsset().getId(),
        content.getSourceType(),
        content.getTitle(),
        content.getUrl(),
        content.getNews() == null ? content.getImageUrl() : content.getNews().getImageUrl(),
        content.getCreatedAt(),
        content.getPublishedDate());
  }
}
