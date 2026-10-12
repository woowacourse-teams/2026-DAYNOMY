package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortfolioAnalysis", description = "포트폴리오 분석 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/portfolio/analysis")
public class SavedPortfolioAnalysisController {

  private final SavedPortfolioAnalysisService service;

  @Operation(
      summary = "내 포트폴리오 저장 분석 조회",
      description = "AI를 호출하지 않습니다. 자산 변경·진행 중·최근 실패 상태에서도 이전 분석을 반환합니다.")
  @GetMapping
  public SavedPortfolioAnalysisResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.get(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 분석 생성 또는 재사용",
      description =
          "DB의 보유 자산과 가격으로 분석합니다. 자산·비중·분석 기준이 같으면 기존 결과를 반환하며 확인 성공 시각은 갱신하지 않습니다. 새 뉴스 확인과는 별개입니다. 동시 분석은 409를 반환합니다.")
  @PostMapping
  public SavedPortfolioAnalysisResponse analyze(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.analyze(member.memberId(), null);
  }
}
