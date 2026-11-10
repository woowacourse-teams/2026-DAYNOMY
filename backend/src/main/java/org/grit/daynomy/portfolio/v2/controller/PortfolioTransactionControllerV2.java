package org.grit.daynomy.portfolio.v2.controller;

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
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.v2.service.PortfolioTransactionServiceV2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio V2", description = "포트폴리오 대시보드 거래 등록 V2 API")
@RequiredArgsConstructor
@RequestMapping("/api/v2/users/me/portfolio/transactions")
@RestController
public class PortfolioTransactionControllerV2 {

  private final PortfolioTransactionServiceV2 service;

  @Operation(
      summary = "매수·매도 거래 등록 V2",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 거래 입력·응답 형식은 기존 거래 등록 API와 같습니다. 처음 거래하면 포트폴리오를 생성하며, 거래일은 해당 종목의 마지막 보유 수량 등록·조정일 이후(당일 포함)여야 합니다. 거래 저장·보유자산·과거 스냅샷 갱신을 한 트랜잭션으로 처리합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
  @Parameter(
      name = "X-XSRF-TOKEN",
      in = ParameterIn.HEADER,
      required = true,
      description = "GET /api/auth/csrf로 발급한 CSRF 토큰. XSRF-TOKEN 쿠키와 같은 값",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "처리 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PortfolioTransactionResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "입력 검증 실패(INVALID_REQUEST), 미국 거래 환율 누락·국내 환율 오류(INVALID_TRANSACTION_EXCHANGE_RATE), 보유 수량 등록·조정일 이전 거래(INVALID_PORTFOLIO_TRADE_DATE), 원장 재계산 시 매도 수량 부족(INSUFFICIENT_PORTFOLIO_QUANTITY)",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요 또는 유효하지 않은 인증 정보: UNAUTHORIZED, INVALID_TOKEN",
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
        description = "회원·종목·포트폴리오 없음: MEMBER_NOT_FOUND, ASSET_NOT_FOUND, PORTFOLIO_NOT_FOUND",
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
  @PostMapping
  public ResponseEntity<PortfolioTransactionResponse> transact(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioTransactionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.transact(member.memberId(), request));
  }
}
