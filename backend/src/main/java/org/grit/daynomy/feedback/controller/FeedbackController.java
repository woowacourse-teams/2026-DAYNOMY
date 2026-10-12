package org.grit.daynomy.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
  @PostMapping
  public ResponseEntity<Void> createFeedback(@Valid @RequestBody FeedbackCreateRequest request) {
    feedbackService.create(request);
    return ResponseEntity.noContent().build();
  }
}
