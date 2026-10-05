package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주식 관련 자료", description = "종목에 연결된 이슈·뉴스·외부 자료 조회 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/assets/{assetId}/contents")
@RestController
public class AssetContentController {

  private final AssetContentService assetContentService;

  @Operation(summary = "종목 관련 자료 조회", description = "종목에 연결된 자료를 최신 등록순으로 조회합니다.")
  @GetMapping
  public ResponseEntity<AssetContentsResponse> getContents(
      @Parameter(description = "자산 ID", example = "1") @PathVariable @Positive Long assetId) {
    return ResponseEntity.ok(assetContentService.getContents(assetId));
  }
}
