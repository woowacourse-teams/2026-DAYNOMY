package org.grit.daynomy.market.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
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

  @Operation(
      summary = "뉴스 시장 분석 조회",
      description = "발행된 뉴스의 시장 분석을 조회합니다. 뉴스·분석이 없거나 미발행 뉴스 또는 risk/watchPoint가 비어 있는 분석은 404입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: newsId가 숫자 형식이 아님",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "MARKET_ANALYSIS_NOT_FOUND: 공개 가능한 시장 분석이 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<MarketAnalysisResponse> getMarketAnalysis(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId) {
    return ResponseEntity.ok(marketAnalysisService.getMarketAnalysis(newsId));
  }
}
