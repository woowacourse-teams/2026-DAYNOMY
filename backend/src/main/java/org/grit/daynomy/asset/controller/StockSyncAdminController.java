package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockSyncResponse;
import org.grit.daynomy.asset.service.StockMasterSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Stock", description = "관리자 국내 주식 종목 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/stocks")
@RestController
public class StockSyncAdminController {

  private final StockMasterSyncService stockMasterSyncService;

  @Operation(summary = "국내 주식 종목 동기화", description = "KOSPI·KOSDAQ 상장 종목을 즉시 동기화합니다.")
  @PostMapping("/sync")
  public ResponseEntity<StockSyncResponse> synchronize() {
    return ResponseEntity.ok(StockSyncResponse.from(stockMasterSyncService.synchronize()));
  }
}
