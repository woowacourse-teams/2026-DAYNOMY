package org.grit.daynomy.person.controller;

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
import org.grit.daynomy.common.response.ErrorResponse;
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
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidatePageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "필터 또는 페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<FeaturedPersonPostCandidatePageResponse> getPendingCandidates(
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size,
      @Parameter(description = "주요 인물 ID. 생략하면 전체 조회", example = "1")
          @RequestParam(required = false)
          @Positive
          Long personId,
      @Parameter(description = "콘텐츠 출처. 생략하면 전체 조회", example = "YOUTUBE")
          @RequestParam(required = false)
          ContentSourceType sourceType) {
    return ResponseEntity.ok(inboxService.getPendingCandidates(page, size, personId, sourceType));
  }

  @Operation(summary = "전체 주요 인물 게시물 후보 선택 일괄 승인")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "일괄 처리 결과 반환",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidateBulkResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/bulk-approve")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkApprove(
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.approve(request.candidateIds()));
  }

  @Operation(summary = "전체 주요 인물 게시물 후보 선택 일괄 거절")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "일괄 처리 결과 반환",
        content =
            @Content(
                schema = @Schema(implementation = FeaturedPersonPostCandidateBulkResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/bulk-reject")
  public ResponseEntity<FeaturedPersonPostCandidateBulkResponse> bulkReject(
      @Valid @RequestBody FeaturedPersonPostCandidateBulkRequest request) {
    return ResponseEntity.ok(bulkService.reject(request.candidateIds()));
  }
}
