package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.dto.AssetRankingSyncResponse;
import org.grit.daynomy.asset.service.AssetRankingSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "AdminAssetRanking", description = "관리자 자산 순위 동기화 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets/kosdaq/top")
@RestController
public class AssetRankingAdminController {

  private final AssetRankingSyncService assetRankingSyncService;

  @Operation(summary = "코스닥 대표 종목 순위 수동 동기화", description = "관리자가 코스닥 시가총액 상위 150개 순위를 즉시 동기화합니다.")
  @PostMapping("/sync")
  public ResponseEntity<AssetRankingSyncResponse> syncKosdaqTopRankings() {
    log.info("Starting manual KOSDAQ top rankings synchronization");
    int savedCount = assetRankingSyncService.syncKosdaqTopRankings();
    log.info("Finished manual KOSDAQ top rankings synchronization: savedCount={}", savedCount);

    return ResponseEntity.ok(new AssetRankingSyncResponse(savedCount));
  }
}
