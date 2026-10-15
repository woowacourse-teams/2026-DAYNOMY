package org.grit.daynomy.portfolio.v2.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/users/me/portfolio/analysis")
public class SavedPortfolioAnalysisV2Controller {
  private final SavedPortfolioAnalysisService service;

  public SavedPortfolioAnalysisV2Controller(
      @Qualifier("savedPortfolioAnalysisV2") SavedPortfolioAnalysisService service) {
    this.service = service;
  }

  @Operation(summary = "포트폴리오 분석 V2: 가격 일괄 조회 및 종목 AI 결과 재사용")
  @PostMapping
  public SavedPortfolioAnalysisResponse analyze(
      @AuthenticationPrincipal AuthenticatedMember member) {
    return service.analyze(member.memberId(), null);
  }
}
