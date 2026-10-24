package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.dto.SocialPostSearchResponse;
import org.grit.daynomy.content.dto.YouTubeSearchResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Asset Related Content", description = "관리자용 종목 관련 자료 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets/{assetId}/contents")
@RestController
public class AdminAssetContentController {

  private final AssetContentService assetContentService;

  @Operation(summary = "종목 관련 자료 등록")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록 성공",
        content = @Content(schema = @Schema(implementation = AssetContentResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 등록된 URL",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<AssetContentResponse> createContent(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId,
      @Valid @RequestBody AssetContentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(assetContentService.createContent(assetId, request));
  }

  @Operation(summary = "종목 관련 자료 목록 조회")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AssetContentsResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "자산 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<AssetContentsResponse> getContents(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId) {
    return ResponseEntity.ok(assetContentService.getContents(assetId));
  }

  @Operation(summary = "YouTube 영상 검색")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = @Content(schema = @Schema(implementation = YouTubeSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "자산 ID 또는 검색어 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/youtube-search")
  public ResponseEntity<YouTubeSearchResponse> searchYouTube(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId,
      @Parameter(description = "검색어(2~100자)", example = "삼성전자")
          @RequestParam
          @jakarta.validation.constraints.Size(min = 2, max = 100)
          String keyword) {
    return ResponseEntity.ok(assetContentService.searchYouTube(assetId, keyword.strip()));
  }

  @Operation(summary = "Threads·X 게시물 검색")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = @Content(schema = @Schema(implementation = SocialPostSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "자산 ID 또는 검색어 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/social-search")
  public ResponseEntity<SocialPostSearchResponse> searchSocialPosts(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId,
      @Parameter(description = "검색어(2~100자)", example = "삼성전자")
          @RequestParam
          @jakarta.validation.constraints.Size(min = 2, max = 100)
          String keyword) {
    return ResponseEntity.ok(assetContentService.searchSocialPosts(assetId, keyword.strip()));
  }

  @Operation(summary = "종목 관련 자료 삭제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "자산 또는 콘텐츠 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "자산 또는 관련 자료를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> deleteContent(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId,
      @Parameter(description = "관련 자료 ID", example = "10") @PathVariable @Positive Long contentId) {
    assetContentService.deleteContent(assetId, contentId);
    return ResponseEntity.noContent().build();
  }
}
