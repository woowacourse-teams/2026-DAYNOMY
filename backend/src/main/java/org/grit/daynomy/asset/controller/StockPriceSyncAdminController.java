package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceBackfillResponse;
import org.grit.daynomy.asset.dto.StockPriceSyncResponse;
import org.grit.daynomy.asset.service.StockPriceSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Stock", description = "관리자 국내 주식·ETF 종목 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/stocks/prices")
@RestController
public class StockPriceSyncAdminController {

  private final StockPriceSyncService stockPriceSyncService;

  @Operation(
      summary = "국내 주식·ETF 종가 동기화",
      description = "최근 2개 거래일의 KOSPI·KOSDAQ 주식과 ETF 종가를 즉시 동기화합니다.")
  @PostMapping("/sync")
  public ResponseEntity<StockPriceSyncResponse> synchronize() {
    return ResponseEntity.ok(StockPriceSyncResponse.from(stockPriceSyncService.synchronize()));
  }

  @Operation(summary = "국내 주식·ETF 종가 백필", description = "지정한 31일 이내 기간의 거래일 종가를 동기화합니다.")
  @PostMapping("/backfill")
  public ResponseEntity<StockPriceBackfillResponse> backfill(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        StockPriceBackfillResponse.from(stockPriceSyncService.backfill(from, to)));
  }
}
