package org.grit.daynomy.market.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.market.dto.MarketAnalysisResponse;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MarketAnalysis", description = "뉴스 시장 분석 API")
@RequiredArgsConstructor
@RequestMapping("/api/news/{newsId}/market-analysis")
@RestController
public class MarketAnalysisController {

  private final MarketAnalysisService marketAnalysisService;

  @Operation(summary = "뉴스 시장 분석 조회", description = "뉴스 ID로 해당 뉴스의 시장 분석을 조회합니다.")
  @GetMapping
  public ResponseEntity<MarketAnalysisResponse> getMarketAnalysis(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId) {
    return ResponseEntity.ok(marketAnalysisService.getMarketAnalysis(newsId));
  }
}
