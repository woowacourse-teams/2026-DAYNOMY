package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
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
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidatesResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<FeaturedPersonPostCandidatesResponse> getPendingCandidates(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId) {
    return ResponseEntity.ok(collectionService.getPendingCandidates(personId));
  }

  @Operation(summary = "주요 인물 공식 프로필 게시물 즉시 수집")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "수집 성공",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidatesResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 또는 수집 기간 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/collect")
  public ResponseEntity<FeaturedPersonPostCandidatesResponse> collect(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Parameter(description = "수집 시작일", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "수집 종료일", example = "2026-10-23")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return ResponseEntity.ok(collectionService.collect(personId, from, to));
  }

  @Operation(summary = "주요 인물 게시물 후보 승인")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "승인 성공",
        content = @Content(schema = @Schema(implementation = FeaturedPersonPostResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 또는 후보 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물 또는 게시물 후보를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "대기 중이 아닌 후보 또는 이미 등록된 게시물",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{candidateId}/approve")
  public ResponseEntity<FeaturedPersonPostResponse> approve(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Parameter(description = "게시물 후보 ID", example = "10") @PathVariable @Positive
          Long candidateId) {
    return ResponseEntity.ok(collectionService.approve(personId, candidateId));
  }

  @Operation(summary = "주요 인물 게시물 후보 거절")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "거절 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "인물 또는 후보 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물 또는 게시물 후보를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "대기 중이 아닌 게시물 후보",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{candidateId}/reject")
  public ResponseEntity<Void> reject(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Parameter(description = "게시물 후보 ID", example = "10") @PathVariable @Positive
          Long candidateId) {
    collectionService.reject(personId, candidateId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "주요 인물 게시물 후보 선택 일괄 승인")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "일괄 처리 결과 반환",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidateBulkResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 또는 요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/bulk-approve")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkApprove(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.approve(personId, request.candidateIds()));
  }

  @Operation(summary = "주요 인물 게시물 후보 선택 일괄 거절")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "일괄 처리 결과 반환",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidateBulkResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 또는 요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/bulk-reject")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkReject(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.reject(personId, request.candidateIds()));
  }
}
