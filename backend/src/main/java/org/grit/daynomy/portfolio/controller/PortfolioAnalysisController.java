package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortfolioAnalysis", description = "뉴스 포트폴리오 분석 API")
@RequiredArgsConstructor
@RequestMapping("/api/news/{newsId}/portfolio-analysis")
@RestController
public class PortfolioAnalysisController {

  private final PortfolioAnalysisService portfolioAnalysisService;

  @Operation(summary = "뉴스 포트폴리오 분석 조회", description = "로그인한 회원이 북마크한 자산에 뉴스가 미치는 영향을 분석합니다.")
  @GetMapping
  public ResponseEntity<PortfolioAnalysisResponse> getPortfolioAnalysis(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId) {
    return ResponseEntity.ok(
        portfolioAnalysisService.getPortfolioAnalysis(authenticatedMember.memberId(), newsId));
  }
}
