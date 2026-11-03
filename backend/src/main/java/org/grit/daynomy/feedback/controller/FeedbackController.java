package org.grit.daynomy.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.feedback.dto.FeedbackCreateRequest;
import org.grit.daynomy.feedback.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Feedback", description = "서비스 피드백 접수 API")
@RequiredArgsConstructor
@RequestMapping("/api/feedback")
@RestController
public class FeedbackController {

  private final FeedbackService feedbackService;

  @Operation(summary = "피드백 등록")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "등록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<Void> createFeedback(@Valid @RequestBody FeedbackCreateRequest request) {
    feedbackService.create(request);
    return ResponseEntity.noContent().build();
  }
}
