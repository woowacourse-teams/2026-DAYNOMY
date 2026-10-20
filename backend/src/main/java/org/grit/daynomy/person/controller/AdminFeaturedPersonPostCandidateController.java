package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatesResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateBulkService;
import org.grit.daynomy.person.service.FeaturedPersonPostCollectionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Featured People", description = "관리자용 주요 인물 게시물 수집 후보 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/people/{personId}/post-candidates")
@RestController
public class AdminFeaturedPersonPostCandidateController {

  private final FeaturedPersonPostCollectionService collectionService;
  private final FeaturedPersonPostCandidateBulkService bulkService;

  @Operation(summary = "승인 대기 중인 주요 인물 게시물 후보 조회")
  @GetMapping
  public ResponseEntity<FeaturedPersonPostCandidatesResponse> getPendingCandidates(
      @PathVariable @Positive Long personId) {
    return ResponseEntity.ok(collectionService.getPendingCandidates(personId));
  }

  @Operation(summary = "주요 인물 공식 프로필 게시물 즉시 수집")
  @PostMapping("/collect")
  public ResponseEntity<FeaturedPersonPostCandidatesResponse> collect(
      @PathVariable @Positive Long personId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(collectionService.collect(personId, from, to));
  }

  @Operation(summary = "주요 인물 게시물 후보 승인")
  @PostMapping("/{candidateId}/approve")
  public ResponseEntity<FeaturedPersonPostResponse> approve(
      @PathVariable @Positive Long personId, @PathVariable @Positive Long candidateId) {
    return ResponseEntity.ok(collectionService.approve(personId, candidateId));
  }

  @Operation(summary = "주요 인물 게시물 후보 거절")
  @PostMapping("/{candidateId}/reject")
  public ResponseEntity<Void> reject(
      @PathVariable @Positive Long personId, @PathVariable @Positive Long candidateId) {
    collectionService.reject(personId, candidateId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "주요 인물 게시물 후보 선택 일괄 승인")
  @PostMapping("/bulk-approve")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkApprove(
      @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.approve(personId, request.candidateIds()));
  }

  @Operation(summary = "주요 인물 게시물 후보 선택 일괄 거절")
  @PostMapping("/bulk-reject")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkReject(
      @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.reject(personId, request.candidateIds()));
  }
}
