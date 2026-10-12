package org.grit.daynomy.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.feedback.dto.AdminFeedbackPageResponse;
import org.grit.daynomy.feedback.dto.AdminFeedbackResponse;
import org.grit.daynomy.feedback.dto.FeedbackStatusUpdateRequest;
import org.grit.daynomy.feedback.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Feedback", description = "관리자용 피드백 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/feedbacks")
@RestController
public class AdminFeedbackController {

  private final FeedbackService feedbackService;

  @Operation(summary = "피드백 목록 조회")
  @GetMapping
  public ResponseEntity<AdminFeedbackPageResponse> getFeedbacks(
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") int page,
      @RequestParam(defaultValue = "15")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size) {
    return ResponseEntity.ok(feedbackService.getPage(page, size));
  }

  @Operation(summary = "피드백 상세 조회")
  @GetMapping("/{id}")
  public ResponseEntity<AdminFeedbackResponse> getFeedback(
      @Parameter(description = "피드백 ID", example = "1") @PathVariable @Positive Long id) {
    return ResponseEntity.ok(feedbackService.getDetail(id));
  }

  @Operation(summary = "피드백 상태 수정")
  @PatchMapping("/{id}")
  public ResponseEntity<AdminFeedbackResponse> updateFeedbackStatus(
      @PathVariable @Positive Long id, @Valid @RequestBody FeedbackStatusUpdateRequest request) {
    return ResponseEntity.ok(feedbackService.updateStatus(id, request));
  }

  @Operation(summary = "피드백 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFeedback(@PathVariable @Positive Long id) {
    feedbackService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
