package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private final SavedPortfolioAnalysisService savedPortfolioAnalysisService;

  @Operation(
      summary = "포트폴리오 분석",
      description =
          "비회원은 요청 자산으로 세션당 1회 체험합니다. 회원은 DB에 저장한 자산과 요청이 일치해야 하며, 동일 자산·분석 기준의 저장 결과가 있으면 재사용합니다. 재사용은 새 소식 확인을 의미하지 않습니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "분석 성공",
        content = @Content(schema = @Schema(implementation = PortfolioAnalysisResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "포트폴리오 입력 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "비회원 분석 체험 소진 또는 회원 CSRF 검증 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "분석 진행 중 또는 저장 자산 불일치·가격 확인 불가",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<PortfolioAnalysisResponse> analyzePortfolio(
      @Valid @RequestBody PortfolioAnalysisRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    return ResponseEntity.ok(
        authenticatedMember == null
            ? portfolioAnalysisService.analyze(request, false)
            : savedPortfolioAnalysisService
                .analyze(authenticatedMember.memberId(), request)
                .analysis());
  }
}
