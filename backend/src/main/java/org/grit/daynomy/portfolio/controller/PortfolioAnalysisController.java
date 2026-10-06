package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortfolioAnalysis", description = "포트폴리오 분석 API")
@RequiredArgsConstructor
@RequestMapping("/api/portfolio/analysis")
@RestController
public class PortfolioAnalysisController {

  private final PortfolioAnalysisService portfolioAnalysisService;

  @Operation(summary = "포트폴리오 분석", description = "요청한 전체 포트폴리오 자산에 오늘의 주요 이슈가 미치는 영향을 분석합니다.")
  @PostMapping
  public ResponseEntity<PortfolioAnalysisResponse> analyzePortfolio(
      @Valid @RequestBody PortfolioAnalysisRequest request) {
    return ResponseEntity.ok(portfolioAnalysisService.analyze(request));
  }
}
