package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceSyncResponse;
import org.grit.daynomy.asset.dto.UsStockSyncResponse;
import org.grit.daynomy.asset.service.UsStockSyncService;
import org.grit.daynomy.asset.service.UsStockSyncWorkflowService;
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
  private final UsStockSyncWorkflowService workflow;

  @Operation(
      summary = "미국 주식·ETF 종목 동기화",
      description =
          "USD 보통주·ETF 목록 갱신 후 한투 한글명을 별도 트랜잭션으로 보충합니다. 종목 실패는 HTTP 오류, 한글명만 실패하면 종목 결과와 koreanNameErrorCode를 200으로 반환합니다. koreanNames와 koreanNameErrorCode 중 하나만 값이 있습니다.")
  @PostMapping("/sync")
  public UsStockSyncResponse synchronize() {
    return UsStockSyncResponse.from(workflow.synchronize());
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
