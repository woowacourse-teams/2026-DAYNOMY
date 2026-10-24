package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;

public record FeaturedPersonPostCandidateInboxItemResponse(
    @Schema(description = "게시물 후보 ID", example = "1") Long id,
    @Schema(description = "주요 인물 ID", example = "10") Long personId,
    @Schema(description = "인물 이름", example = "일론 머스크") String personName,
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/people/10.png")
        String personProfileImageUrl,
    @Schema(description = "게시물 출처", example = "X") ContentSourceType sourceType,
    @Schema(description = "게시물 제목", example = "시장 전망에 대한 의견") String title,
    @Schema(description = "게시물 URL", example = "https://x.com/example/status/1") String url,
    @Schema(description = "원문 게시 시각", example = "2026-10-22T09:00:00Z") Instant publishedAt,
    @Schema(description = "검토 상태", example = "PENDING") FeaturedPersonPostStatus status,
    @Schema(description = "수집 시각", example = "2026-10-23T09:00:00Z") Instant createdAt) {

  public static FeaturedPersonPostCandidateInboxItemResponse from(FeaturedPersonPost candidate) {
    return new FeaturedPersonPostCandidateInboxItemResponse(
        candidate.getId(),
        candidate.getPerson().getId(),
        candidate.getPerson().getName(),
        candidate.getPerson().getProfileImageUrl(),
        candidate.getSourceType(),
        candidate.getTitle(),
        candidate.getUrl(),
        candidate.getPublishedAt(),
        candidate.getStatus(),
        candidate.getCreatedAt());
  }
}
