package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.AdminIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminIndustryAssetsResponse;
import org.grit.daynomy.asset.dto.AdminIndustryCreateRequest;
import org.grit.daynomy.asset.dto.AdminIndustryResponse;
import org.grit.daynomy.asset.dto.AdminIndustryUpdateRequest;
import org.grit.daynomy.asset.service.AdminIndustryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Industry", description = "관리자 업종 기준정보 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/industries")
@RestController
public class AdminIndustryController {

  private final AdminIndustryService adminIndustryService;

  @Operation(summary = "업종 전체 조회")
  @GetMapping
  public AdminIndustriesResponse getIndustries() {
    return adminIndustryService.getIndustries();
  }

  @Operation(summary = "업종 생성")
  @PostMapping
  public ResponseEntity<AdminIndustryResponse> createIndustry(
      @Valid @RequestBody AdminIndustryCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminIndustryService.createIndustry(request));
  }

  @Operation(summary = "업종 수정")
  @PatchMapping("/{industryId}")
  public AdminIndustryResponse updateIndustry(
      @PathVariable @Positive Long industryId,
      @Valid @RequestBody AdminIndustryUpdateRequest request) {
    return adminIndustryService.updateIndustry(industryId, request);
  }

  @Operation(summary = "업종 연결 종목 조회")
  @GetMapping("/{industryId}/assets")
  public AdminIndustryAssetsResponse getIndustryAssets(
      @PathVariable @Positive Long industryId,
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size) {
    return adminIndustryService.getIndustryAssets(industryId, page, size);
  }

  @Operation(summary = "업종에 종목 연결")
  @PutMapping("/{industryId}/assets/{assetId}")
  public ResponseEntity<Void> addIndustryAsset(
      @PathVariable @Positive Long industryId, @PathVariable @Positive Long assetId) {
    adminIndustryService.addIndustryAsset(industryId, assetId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "업종에서 종목 연결 해제")
  @DeleteMapping("/{industryId}/assets/{assetId}")
  public ResponseEntity<Void> removeIndustryAsset(
      @PathVariable @Positive Long industryId, @PathVariable @Positive Long assetId) {
    adminIndustryService.removeIndustryAsset(industryId, assetId);
    return ResponseEntity.noContent().build();
  }
}
