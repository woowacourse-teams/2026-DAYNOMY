package org.grit.daynomy.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.feedback.dto.AdminFeedbackCreateRequest;
import org.grit.daynomy.feedback.dto.AdminFeedbackPageResponse;
import org.grit.daynomy.feedback.dto.AdminFeedbackResponse;
import org.grit.daynomy.feedback.dto.FeedbackStatusUpdateRequest;
import org.grit.daynomy.feedback.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  @Operation(summary = "관리자 피드백 등록")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "등록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<Void> createFeedback(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody AdminFeedbackCreateRequest request) {
    feedbackService.createAdmin(member.memberId(), request);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "피드백 목록 조회")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AdminFeedbackPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<AdminFeedbackPageResponse> getFeedbacks(
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "15")
          @RequestParam(defaultValue = "15")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size) {
    return ResponseEntity.ok(feedbackService.getPage(page, size));
  }

  @Operation(summary = "피드백 상세 조회")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AdminFeedbackResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "피드백 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "피드백을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{id}")
  public ResponseEntity<AdminFeedbackResponse> getFeedback(
      @Parameter(description = "피드백 ID", example = "1") @PathVariable @Positive Long id) {
    return ResponseEntity.ok(feedbackService.getDetail(id));
  }

  @Operation(summary = "피드백 상태 수정")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = AdminFeedbackResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "피드백 ID 또는 요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "피드백을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/{id}")
  public ResponseEntity<AdminFeedbackResponse> updateFeedbackStatus(
      @Parameter(description = "피드백 ID", example = "1") @PathVariable @Positive Long id,
      @Valid @RequestBody FeedbackStatusUpdateRequest request) {
    return ResponseEntity.ok(feedbackService.updateStatus(id, request));
  }

  @Operation(summary = "피드백 삭제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "피드백 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "피드백을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFeedback(
      @Parameter(description = "피드백 ID", example = "1") @PathVariable @Positive Long id) {
    feedbackService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
