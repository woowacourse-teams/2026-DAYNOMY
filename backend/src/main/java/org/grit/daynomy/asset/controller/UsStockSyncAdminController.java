package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceSyncResponse;
import org.grit.daynomy.asset.dto.StockSyncResponse;
import org.grit.daynomy.asset.service.UsStockSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin US Stock", description = "관리자 미국 주식·ETF 종목 및 일별 종가 관리")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/stocks/us")
public class UsStockSyncAdminController {
  private final UsStockSyncService service;

  @Operation(
      summary = "미국 주식·ETF 종목 동기화",
      description = "주요 미국 거래소의 USD 보통주와 ETF를 동기화합니다. OTC·우선주·펀드·워런트는 제외합니다.")
  @PostMapping("/sync")
  public StockSyncResponse synchronize() {
    return StockSyncResponse.from(service.synchronizeMaster());
  }

  @Operation(
      summary = "미국 주식·ETF 종가 동기화",
      description = "뉴욕 기준 과거 날짜의 종가를 수집합니다. 날짜 생략 시 어제부터 최대 10일간 최근 종가를 찾습니다.")
  @PostMapping("/prices/sync")
  public StockPriceSyncResponse synchronizePrices(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return StockPriceSyncResponse.from(service.synchronizePrices(date));
  }
}
