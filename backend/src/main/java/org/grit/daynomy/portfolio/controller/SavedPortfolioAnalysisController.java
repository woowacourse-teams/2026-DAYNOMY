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
      summary = "내 포트폴리오 분석 변경 이력 조회",
      description =
          "본인의 최근 분석 이력 최대 10개를 최신순으로 반환합니다. 삭제된 오래된 이력을 가리키는 previousAnalysisId는 null입니다.")
  @GetMapping("/history")
  public java.util.List<org.grit.daynomy.portfolio.dto.PortfolioAnalysisHistoryResponse> history(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.history(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 저장 분석 조회",
      description = "AI를 호출하지 않습니다. 자산 변경·진행 중·최근 실패 상태에서도 이전 분석을 반환합니다.")
  @GetMapping
  public SavedPortfolioAnalysisResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.get(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 새 근거 확인",
      description =
          "실제 웹 검색을 실행합니다. 문체·출처 오류는 최대 한 번 교정하며, 일부 종목의 검증 실패 시 유효한 종목과 기존 근거를 보존합니다. 유효한 동일 근거이면 기존 분석과 생성 시각을 유지하고 최근 확인 시각만 갱신합니다. 이전·현재 모두 근거가 없거나 새 사건으로 확인하기 어려우면 RECHECK_REQUIRED, 검색 실패 시 오류를 반환하며 이전 분석을 보존합니다. 동시 요청은 409입니다.")
  @PostMapping("/refresh")
  public SavedPortfolioAnalysisResponse refresh(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.refresh(member.memberId());
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
