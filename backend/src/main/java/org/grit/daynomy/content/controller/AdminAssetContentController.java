package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.dto.YouTubeSearchResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Tag(name = "Admin Asset Related Content", description = "관리자용 종목 관련 자료 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets/{assetId}/contents")
@RestController
public class AdminAssetContentController {

  private final AssetContentService assetContentService;

  @Operation(summary = "종목 관련 자료 등록")
  @PostMapping
  public ResponseEntity<AssetContentResponse> createContent(
          @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId,
          @Valid @RequestBody AssetContentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(assetContentService.createContent(assetId, request));
  }

  @Operation(summary = "종목 관련 자료 목록 조회")
  @GetMapping
  public ResponseEntity<AssetContentsResponse> getContents(
      @PathVariable @Positive Long assetId) {
    return ResponseEntity.ok(assetContentService.getContents(assetId));
  }

  @Operation(summary = "YouTube 영상 검색")
  @GetMapping("/youtube-search")
  public ResponseEntity<YouTubeSearchResponse> searchYouTube(
      @PathVariable @Positive Long assetId,
      @RequestParam @jakarta.validation.constraints.Size(min = 2, max = 100) String keyword) {
    return ResponseEntity.ok(assetContentService.searchYouTube(assetId, keyword.strip()));
  }

  @Operation(summary = "종목 관련 자료 삭제")
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> deleteContent(
      @PathVariable @Positive Long assetId, @PathVariable @Positive Long contentId) {
    assetContentService.deleteContent(assetId, contentId);
    return ResponseEntity.noContent().build();
  }
}
