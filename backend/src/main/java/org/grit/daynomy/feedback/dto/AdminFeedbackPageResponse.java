package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.feedback.domain.Feedback;
import org.springframework.data.domain.Page;

public record AdminFeedbackPageResponse(
    @Schema(description = "피드백 목록") List<AdminFeedbackResponse> items,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "15") int size,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages,
    @Schema(description = "전체 피드백 수", example = "42") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {

  public static AdminFeedbackPageResponse from(Page<Feedback> page) {
    return new AdminFeedbackPageResponse(
        page.getContent().stream().map(AdminFeedbackResponse::from).toList(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
