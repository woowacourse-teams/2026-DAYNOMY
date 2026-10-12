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

  @Operation(
      summary = "포트폴리오 분석",
      description = "요청한 전체 포트폴리오 자산에 오늘의 주요 이슈가 미치는 영향을 분석합니다. 비회원은 브라우저 세션당 1회 체험할 수 있습니다.")
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
        description = "비회원 분석 체험 소진",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<PortfolioAnalysisResponse> analyzePortfolio(
      @Valid @RequestBody PortfolioAnalysisRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    return ResponseEntity.ok(
        portfolioAnalysisService.analyze(request, authenticatedMember != null));
  }
}
