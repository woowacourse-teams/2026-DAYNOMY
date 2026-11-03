package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.ContentCollectionRematchResponse;
import org.grit.daynomy.content.dto.ContentCollectionReviewApproveRequest;
import org.grit.daynomy.content.dto.ContentCollectionReviewBulkApproveRequest;
import org.grit.daynomy.content.dto.ContentCollectionReviewBulkRejectRequest;
import org.grit.daynomy.content.dto.ContentCollectionReviewPageResponse;
import org.grit.daynomy.content.service.ContentCollectionReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Content Collection Review", description = "관리자용 수집 콘텐츠 검수 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/content-collection-reviews")
@RestController
public class AdminContentCollectionReviewController {

  private final ContentCollectionReviewService reviewService;

  @Operation(summary = "승인 대기 수집 콘텐츠 조회")
  @GetMapping
  public ResponseEntity<ContentCollectionReviewPageResponse> getPending(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(reviewService.getPending(page, size));
  }

  @Operation(summary = "수집 콘텐츠 승인")
  @PostMapping("/{contentId}/approve")
  public ResponseEntity<Void> approve(
      @PathVariable @Positive Long contentId,
      @Valid @RequestBody ContentCollectionReviewApproveRequest request) {
    reviewService.approve(contentId, request.assetIds(), request.publishedDate());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "수집 콘텐츠 거절")
  @PostMapping("/{contentId}/reject")
  public ResponseEntity<Void> reject(@PathVariable @Positive Long contentId) {
    reviewService.reject(contentId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "수집 콘텐츠 선택 일괄 승인")
  @PostMapping("/bulk-approve")
  public ResponseEntity<Void> approveAll(
      @Valid @RequestBody ContentCollectionReviewBulkApproveRequest request) {
    reviewService.approveAll(request.items());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "수집 콘텐츠 선택 일괄 거절")
  @PostMapping("/bulk-reject")
  public ResponseEntity<Void> rejectAll(
      @Valid @RequestBody ContentCollectionReviewBulkRejectRequest request) {
    reviewService.rejectAll(request.contentIds());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "미매칭 수집 콘텐츠 종목 자동 재매칭")
  @PostMapping("/rematch")
  public ResponseEntity<ContentCollectionRematchResponse> rematch() {
    return ResponseEntity.ok(reviewService.rematchPending());
  }
}
