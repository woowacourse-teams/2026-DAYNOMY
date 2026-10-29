package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.UsStockKoreanNameSyncResponse;
import org.grit.daynomy.asset.service.UsStockKoreanNameSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin US Stock", description = "관리자 미국 주식·ETF 종목 및 일별 종가 관리")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/stocks/us/korean-names")
public class UsStockKoreanNameSyncAdminController {
  private final UsStockKoreanNameSyncService service;

  @Operation(
      summary = "미국 주식·ETF 한글명 동기화",
      description = "한투 NAS/NYS/AMS 파일 전체를 검증한 뒤 기존 상장 종목의 한글명만 저장합니다. 미국 종목 목록 적재가 선행되어야 합니다.")
  @PostMapping("/sync")
  public UsStockKoreanNameSyncResponse synchronize() {
    return UsStockKoreanNameSyncResponse.from(service.synchronize());
  }
}
