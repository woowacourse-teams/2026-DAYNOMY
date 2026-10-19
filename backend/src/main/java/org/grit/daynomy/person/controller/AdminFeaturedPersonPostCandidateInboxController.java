package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatePageResponse;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateBulkService;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Featured People", description = "관리자용 주요 인물 전체 게시물 수집 후보 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/people/post-candidates")
@RestController
public class AdminFeaturedPersonPostCandidateInboxController {

  private final FeaturedPersonPostCandidateInboxService inboxService;
  private final FeaturedPersonPostCandidateBulkService bulkService;

  @Operation(summary = "전체 주요 인물 승인 대기 게시물 후보 조회")
  @GetMapping
  public ResponseEntity<FeaturedPersonPostCandidatePageResponse> getPendingCandidates(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Positive Long personId,
      @RequestParam(required = false) ContentSourceType sourceType) {
    return ResponseEntity.ok(inboxService.getPendingCandidates(page, size, personId, sourceType));
  }

  @Operation(summary = "전체 주요 인물 게시물 후보 선택 일괄 승인")
  @PostMapping("/bulk-approve")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkApprove(
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.approve(request.candidateIds()));
  }

  @Operation(summary = "전체 주요 인물 게시물 후보 선택 일괄 거절")
  @PostMapping("/bulk-reject")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkReject(
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.reject(request.candidateIds()));
  }
}
