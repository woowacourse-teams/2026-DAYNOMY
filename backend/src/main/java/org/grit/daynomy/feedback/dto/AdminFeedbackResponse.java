package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.feedback.domain.Feedback;
import org.grit.daynomy.feedback.domain.FeedbackCategory;
import org.grit.daynomy.feedback.domain.FeedbackStatus;

public record AdminFeedbackResponse(
    @Schema(description = "피드백 ID", example = "1") Long id,
    @Schema(description = "피드백 내용") String content,
    @Schema(description = "피드백 작성 화면 경로", example = "/news/1") String pagePath,
    @Schema(description = "피드백 유형. 일반 사용자 피드백이면 null", example = "BUG")
        FeedbackCategory category,
    @Schema(description = "작성자 회원 ID. 일반 사용자 피드백이면 null", example = "1")
        Long authorMemberId,
    @Schema(description = "작성 당시 작성자 닉네임. 일반 사용자 피드백이면 null", example = "관리자")
        String authorNickname,
    @Schema(description = "피드백 처리 상태", example = "PENDING") FeedbackStatus status,
    @Schema(description = "작성 시각", example = "2026-10-12T09:00:00Z") Instant createdAt,
    @Schema(description = "수정 시각", example = "2026-10-12T09:00:00Z") Instant updatedAt) {

  public static AdminFeedbackResponse from(Feedback feedback) {
    return new AdminFeedbackResponse(
        feedback.getId(),
        feedback.getContent(),
        feedback.getPagePath(),
        feedback.getCategory(),
        feedback.getAuthorMemberId(),
        feedback.getAuthorNickname(),
        feedback.getStatus(),
        feedback.getCreatedAt(),
        feedback.getUpdatedAt());
  }
}
