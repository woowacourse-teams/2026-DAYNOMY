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
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포트폴리오", description = "로컬 보유자산의 평가금액과 수익률을 계산합니다.")
@RequiredArgsConstructor
@RequestMapping("/api/portfolio")
@RestController
public class PortfolioController {

  private final PortfolioCalculationService portfolioCalculationService;

  @Operation(
      summary = "포트폴리오 계산",
      description =
          "로그인 없이 이용할 수 있습니다. 전달한 1~50개 보유자산을 최근 종가로 평가하며 서버에 저장하지 않습니다. 평균 매입가는 원화로 입력하고, 미국 종목 평가는 종가 기준일의 환율로 원화 환산합니다. 종가·환율이 없는 종목도 HTTP 200의 PARTIAL 결과에 포함하며 해당 종목의 평가값은 null입니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
  @Parameter(
      name = "X-XSRF-TOKEN",
      in = ParameterIn.HEADER,
      required = true,
      description = "GET /api/auth/csrf로 발급한 CSRF 토큰. XSRF-TOKEN 쿠키와 같은 값",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "처리 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PortfolioCalculationResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "입력 검증 실패: INVALID_REQUEST. 중복 종목: DUPLICATE_PORTFOLIO_ASSET",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "CSRF 토큰 누락 또는 불일치: FORBIDDEN",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "종목 자체가 존재하지 않음: ASSET_NOT_FOUND. 종가가 없는 경우에는 200 PARTIAL 응답",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "502",
        description = "미국 종목의 평가·과거 스냅샷 계산에 필요한 환율 조회 실패: EODHD_API_REQUEST_FAILED",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "미국 종목 환율 조회 서비스 미설정: EODHD_API_NOT_CONFIGURED",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/calculate")
  public PortfolioCalculationResponse calculate(
      @Valid @RequestBody PortfolioCalculateRequest request) {
    return portfolioCalculationService.calculate(request);
  }
}
