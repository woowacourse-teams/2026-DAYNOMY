package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            description =
                "access_token 쿠키가 있으면 필수. GET /api/auth/csrf 응답 token을 전달하며 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      },
      description =
          "비회원은 세션당 1회 분석을 체험합니다. 빈 자산 목록은 체험을 소진하지 않습니다. 회원은 요청 종목·비중이 저장된 자산과 일치해야 하며, 서버의 보유 수량을 사용합니다. 동일 자산·분석 기준의 저장 결과가 있으면 재사용합니다. 재사용은 새 소식 확인을 의미하지 않습니다. access_token 쿠키가 있는 요청은 CSRF 검증이 필요합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "분석 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PortfolioAnalysisResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "INVALID_REQUEST: 입력 검증 오류, DUPLICATE_PORTFOLIO_ASSET: 종목 중복, INVALID_PORTFOLIO_WEIGHT_TOTAL: 비중 합계가 100이 아님",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED: 비회원 체험 소진, FORBIDDEN: CSRF 검증 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "PORTFOLIO_ANALYSIS_IN_PROGRESS: 분석 진행 중, PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE: 보유자산·가격 없음, PORTFOLIO_ANALYSIS_ASSETS_CHANGED: 분석 중 자산 변경, PORTFOLIO_ANALYSIS_REQUEST_EXPIRED: 분석 요청 만료",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "502",
        description = "AI_PORTFOLIO_ANALYSIS_FAILED: AI 분석 또는 웹 검색 실패. 기존 저장 분석은 보존",
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
