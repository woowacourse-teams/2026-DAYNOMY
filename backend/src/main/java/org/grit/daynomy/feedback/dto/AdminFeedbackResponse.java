package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.feedback.domain.Feedback;
import org.grit.daynomy.feedback.domain.FeedbackStatus;

public record AdminFeedbackResponse(
    @Schema(description = "피드백 ID", example = "1") Long id,
    @Schema(description = "피드백 내용") String content,
    @Schema(description = "피드백 작성 화면 경로", example = "/news/1") String pagePath,
    @Schema(description = "피드백 처리 상태", example = "PENDING") FeedbackStatus status,
    @Schema(description = "작성 시각", example = "2026-10-12T09:00:00Z") Instant createdAt,
    @Schema(description = "수정 시각", example = "2026-10-12T09:00:00Z") Instant updatedAt) {

  public static AdminFeedbackResponse from(Feedback feedback) {
    return new AdminFeedbackResponse(
        feedback.getId(),
        feedback.getContent(),
        feedback.getPagePath(),
        feedback.getStatus(),
        feedback.getCreatedAt(),
        feedback.getUpdatedAt());
  }
}
