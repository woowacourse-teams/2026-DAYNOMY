package org.grit.daynomy.portfolio.v1.controller;

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
@RequestMapping("/api/v1/users/me/portfolio/analysis")
public class SavedPortfolioAnalysisV1Controller {
  private final SavedPortfolioAnalysisService service;

  public SavedPortfolioAnalysisV1Controller(
      @Qualifier("savedPortfolioAnalysisV1") SavedPortfolioAnalysisService service) {
    this.service = service;
  }

  @Operation(summary = "포트폴리오 분석 V1: 종목별 가격 조회 및 기존 AI 분석")
  @PostMapping
  public SavedPortfolioAnalysisResponse analyze(
      @AuthenticationPrincipal AuthenticatedMember member) {
    return service.analyze(member.memberId(), null);
  }
}
