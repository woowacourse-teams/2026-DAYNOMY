package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.service.AssetCandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssetCandidate", description = "북마크 후보 자산 API")
@RequiredArgsConstructor
@RequestMapping("/api/assets")
@RestController
public class AssetCandidateController {

  private final AssetCandidateService assetCandidateService;

  @Operation(
      summary = "코스닥 대표 종목 순위 조회",
      description = "KOSDAQ 시장 종목 중 시가총액 상위 150개를 순위 형태로 조회합니다.")
  @GetMapping("/kosdaq/top")
  public ResponseEntity<AssetCandidatesResponse> getKosdaqTopRankings() {
    return ResponseEntity.ok(assetCandidateService.getKosdaqTopRankings());
  }
}
