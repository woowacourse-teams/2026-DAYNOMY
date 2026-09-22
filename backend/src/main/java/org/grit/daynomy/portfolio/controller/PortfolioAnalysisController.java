package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortfolioAnalysis", description = "뉴스 포트폴리오 분석 API")
@RequiredArgsConstructor
@RequestMapping("/api/news/{newsId}/portfolio-analysis")
@RestController
public class PortfolioAnalysisController {

  private final PortfolioAnalysisService portfolioAnalysisService;

  @Operation(summary = "뉴스 포트폴리오 분석", description = "요청한 포트폴리오 자산에 뉴스가 미치는 영향을 분석합니다.")
  @PostMapping
  public ResponseEntity<PortfolioAnalysisResponse> analyzePortfolio(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId,
      @Valid @RequestBody PortfolioAnalysisRequest request) {
    return ResponseEntity.ok(portfolioAnalysisService.analyze(newsId, request));
  }
}
