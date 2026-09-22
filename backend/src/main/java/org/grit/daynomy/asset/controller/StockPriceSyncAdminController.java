package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceSyncResponse;
import org.grit.daynomy.asset.service.StockPriceSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Stock", description = "관리자 국내 주식 종목 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/stocks/prices")
@RestController
public class StockPriceSyncAdminController {

  private final StockPriceSyncService stockPriceSyncService;

  @Operation(summary = "국내 주식 종가 동기화", description = "최근 거래일의 KOSPI·KOSDAQ 종가를 즉시 동기화합니다.")
  @PostMapping("/sync")
  public ResponseEntity<StockPriceSyncResponse> synchronize() {
    return ResponseEntity.ok(StockPriceSyncResponse.from(stockPriceSyncService.synchronize()));
  }
}
