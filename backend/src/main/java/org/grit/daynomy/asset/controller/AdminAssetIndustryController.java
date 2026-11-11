package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesUpdateRequest;
import org.grit.daynomy.asset.service.AdminIndustryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Asset Industry", description = "관리자 종목별 업종 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets/{assetId}/industries")
@RestController
public class AdminAssetIndustryController {

  private final AdminIndustryService adminIndustryService;

  @Operation(summary = "종목 업종 조회")
  @GetMapping
  public AdminAssetIndustriesResponse getAssetIndustries(@PathVariable @Positive Long assetId) {
    return adminIndustryService.getAssetIndustries(assetId);
  }

  @Operation(summary = "종목 업종 저장")
  @PutMapping
  public AdminAssetIndustriesResponse updateAssetIndustries(
      @PathVariable @Positive Long assetId,
      @Valid @RequestBody AdminAssetIndustriesUpdateRequest request) {
    return adminIndustryService.updateAssetIndustries(assetId, request);
  }
}
