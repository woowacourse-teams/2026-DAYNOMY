package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;

public record FeaturedPersonPostCandidateResponse(
    @Schema(description = "게시물 후보 ID", example = "1") Long id,
    @Schema(description = "게시물 출처", example = "X") ContentSourceType sourceType,
    @Schema(description = "게시물 제목", example = "시장 전망에 대한 의견") String title,
    @Schema(description = "게시물 URL", example = "https://x.com/example/status/1") String url,
    @Schema(description = "원문 게시 시각", example = "2026-10-22T09:00:00Z") Instant publishedAt,
    @Schema(description = "검토 상태", example = "PENDING") FeaturedPersonPostStatus status,
    @Schema(description = "수집 시각", example = "2026-10-23T09:00:00Z") Instant createdAt) {

  public static FeaturedPersonPostCandidateResponse from(FeaturedPersonPost candidate) {
    return new FeaturedPersonPostCandidateResponse(
        candidate.getId(),
        candidate.getSourceType(),
        candidate.getTitle(),
        candidate.getUrl(),
        candidate.getPublishedAt(),
        candidate.getStatus(),
        candidate.getCreatedAt());
  }
}
