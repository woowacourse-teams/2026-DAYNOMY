package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingHistoryResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingsCreateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingsUpdateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPriceHistoryResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionImportRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionImportResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioResponse;
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.SavedPortfolioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Saved Portfolio",
    description =
        "포트폴리오 대시보드의 보유자산·거래·수익률 API. /api/users/me/portfolio와 /api/portfolio는 동일한 동작을 제공합니다.")
@RequiredArgsConstructor
@RequestMapping({"/api/portfolio", "/api/users/me/portfolio"})
@RestController
public class SavedPortfolioController {

  private final SavedPortfolioService savedPortfolioService;
  private final PortfolioSnapshotService snapshotService;
  private final PortfolioPriceHistoryService priceHistoryService;

  @Operation(
      summary = "현재 보유잔고 일괄 등록",
      description = "신규 종목의 수량과 원화 평균 매입가를 현재 보유잔고로 등록합니다. 매수 거래를 만들지 않으며 중복 종목은 거부합니다.")
  @PostMapping("/holdings/bulk")
  public ResponseEntity<SavedPortfolioResponse> createHoldings(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioHoldingsCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(savedPortfolioService.createHoldings(member.memberId(), request));
  }

  @Operation(summary = "보유정보 일괄 정정", description = "수량과 원화 평균 매입가만 정정합니다. 하나라도 실패하면 전체 변경을 취소합니다.")
  @PatchMapping("/holdings")
  public List<SavedPortfolioHoldingResponse> updateHoldings(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioHoldingsUpdateRequest request) {
    return savedPortfolioService.updateHoldings(member.memberId(), request);
  }

  @Operation(
      summary = "CSV 거래 일괄 등록",
      description =
          "검증한 CSV 거래를 날짜순으로 등록합니다. 같은 날짜는 파일 순서를 유지하며 전체 실패 시 롤백합니다. 동일 내용의 재요청은 중복 등록하지 않습니다.")
  @PostMapping("/transactions/import")
  public PortfolioTransactionImportResponse importTransactions(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioTransactionImportRequest request) {
    return savedPortfolioService.importTransactions(member.memberId(), request);
  }

  @Operation(
      summary = "매수·매도 거래 일괄 등록",
      description = "전체 거래를 한 트랜잭션으로 저장합니다. 하나라도 실패하면 전체 변경을 취소합니다.")
  @PostMapping("/transactions/bulk")
  public PortfolioTransactionImportResponse createTransactions(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioTransactionImportRequest request) {
    return savedPortfolioService.createTransactions(member.memberId(), request);
  }

  @Operation(
      summary = "내 포트폴리오 조회",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 현재 보유자산과 거래 원장 기준 누적 실현손익(KRW)을 반환합니다. 포트폴리오가 없으면 holdings는 빈 배열, totalRealizedProfitLoss는 0입니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "처리 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SavedPortfolioResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요 또는 유효하지 않은 인증 정보: UNAUTHORIZED, INVALID_TOKEN",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public SavedPortfolioResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return savedPortfolioService.get(member.memberId());
  }

  @Operation(
      summary = "매수·매도 거래 등록",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 처음 거래하면 포트폴리오를 생성합니다. 거래일은 해당 종목의 마지막 보유 수량 등록·조정일 이후(당일 포함)여야 합니다. 거래를 반영해 현재 보유자산·누적 실현손익·과거 스냅샷을 갱신합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
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
        description = "회원 또는 종목 없음: MEMBER_NOT_FOUND, ASSET_NOT_FOUND",
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
  @PostMapping("/transactions")
  public ResponseEntity<PortfolioTransactionResponse> transact(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioTransactionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(savedPortfolioService.transact(member.memberId(), request));
  }

  @Operation(
      summary = "매수·매도 거래 수정",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 본인의 BUY·SELL 거래만 수정할 수 있습니다. 거래 종목과 매수·매도 구분은 변경할 수 없으며, 본문의 거래일·수량·단가·수수료·세금은 모두 필수입니다. 미국 거래는 환율도 필수입니다. 변경된 원장으로 보유자산·실현손익·과거 스냅샷을 재계산합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
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
        description =
            "포트폴리오 없음 또는 본인의 매수·매도 거래가 아님: PORTFOLIO_NOT_FOUND, PORTFOLIO_TRANSACTION_NOT_FOUND",
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
  @PatchMapping("/transactions/{transactionId}")
  public PortfolioTransactionResponse updateTransaction(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "본인 포트폴리오의 매수·매도 거래 이력 ID", example = "1") @PathVariable
          Long transactionId,
      @Valid @RequestBody PortfolioTransactionUpdateRequest request) {
    return savedPortfolioService.updateTransaction(member.memberId(), transactionId, request);
  }

  @Operation(
      summary = "매수·매도 거래 삭제",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 본인의 BUY·SELL 거래를 삭제한 뒤 보유자산·실현손익·과거 스냅샷을 재계산합니다. 삭제 결과 이후의 매도 수량이 부족해지면 거부합니다. 삭제한 거래 ID와 변경 후 보유자산을 응답합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
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
                schema = @Schema(implementation = PortfolioTransactionResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "원장 재계산 시 매도 수량 부족: INSUFFICIENT_PORTFOLIO_QUANTITY. 거래 ID 형식 오류: INVALID_REQUEST",
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
        description =
            "포트폴리오 없음 또는 본인의 매수·매도 거래가 아님: PORTFOLIO_NOT_FOUND, PORTFOLIO_TRANSACTION_NOT_FOUND",
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
  @DeleteMapping("/transactions/{transactionId}")
  public PortfolioTransactionResponse deleteTransaction(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "본인 포트폴리오의 매수·매도 거래 이력 ID", example = "1") @PathVariable
          Long transactionId) {
    return savedPortfolioService.deleteTransaction(member.memberId(), transactionId);
  }

  @Operation(
      summary = "보유자산 수정",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 이미 보유한 종목의 수량과 원화 기준 평균 매입가를 조정합니다. 두 필드는 모두 필수이며, 값이 바뀐 경우 서울 기준 오늘 날짜로 ADJUSTED 이력을 기록합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
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
                schema = @Schema(implementation = SavedPortfolioHoldingResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 또는 종목 ID 형식 오류: INVALID_REQUEST",
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
        description = "포트폴리오 또는 보유자산 없음: PORTFOLIO_NOT_FOUND, PORTFOLIO_HOLDING_NOT_FOUND",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/holdings/{assetId}")
  public SavedPortfolioHoldingResponse update(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "본인 포트폴리오에 보유한 종목 ID", example = "1") @PathVariable Long assetId,
      @Valid @RequestBody SavedPortfolioHoldingUpdateRequest request) {
    return savedPortfolioService.update(member.memberId(), assetId, request);
  }

  @Operation(
      summary = "보유자산 삭제",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 현재 보유자산을 제거하고 서울 기준 오늘 날짜로 REMOVED 이력을 기록합니다. 매도 거래를 생성하거나 과거 거래 이력을 삭제하는 동작은 아닙니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
  @Parameter(
      name = "X-XSRF-TOKEN",
      in = ParameterIn.HEADER,
      required = true,
      description = "GET /api/auth/csrf로 발급한 CSRF 토큰. XSRF-TOKEN 쿠키와 같은 값",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "처리 성공 (응답 본문 없음)", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "종목 ID 형식 오류: INVALID_REQUEST",
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
        description = "포트폴리오 또는 보유자산 없음: PORTFOLIO_NOT_FOUND, PORTFOLIO_HOLDING_NOT_FOUND",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/holdings/{assetId}")
  public ResponseEntity<Void> remove(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "본인 포트폴리오에 보유한 종목 ID", example = "1") @PathVariable Long assetId) {
    savedPortfolioService.remove(member.memberId(), assetId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "기간별 포트폴리오 수익률 조회",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. from~to(양끝 포함)의 과거 스냅샷을 날짜 오름차순으로 조회합니다. 서울 기준 오늘은 points에서 제외하고, 조회 기간에 포함될 때 currentPoint로 계산하며 저장하지 않습니다. 과거 스냅샷이 2개 미만이면 HTTP 200과 INSUFFICIENT_DATA를 반환합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "처리 성공",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PortfolioPerformanceResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "시작일이 종료일보다 늦음: INVALID_PORTFOLIO_PERIOD. 날짜 형식 오류: INVALID_REQUEST. 필수 쿼리 파라미터 누락은 ProblemDetail 형식",
        content = {
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)),
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))
        }),
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요 또는 유효하지 않은 인증 정보: UNAUTHORIZED, INVALID_TOKEN",
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
  @GetMapping("/performance")
  public PortfolioPerformanceResponse performance(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "조회 시작일(포함). yyyy-MM-dd 형식이며 to 이하", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "조회 종료일(포함). yyyy-MM-dd 형식", example = "2026-10-23")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return snapshotService.performance(member.memberId(), from, to);
  }

  @Operation(
      summary = "포트폴리오 과거 종가 준비",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 요청 범위의 국내·미국 종가 수집과 스냅샷 갱신을 비동기로 시작하거나 진행 상태를 조회합니다. 실제 수집 범위는 서울 기준 최근 5년부터 어제까지로 제한합니다. HTTP 200으로 READY·PROCESSING·FAILED를 반환하며, PROCESSING이면 같은 요청으로 상태를 확인합니다. FAILED 이후에는 retry=true로 재시도합니다. 변경 요청은 GET /api/auth/csrf로 발급한 XSRF-TOKEN 쿠키와 동일한 값을 X-XSRF-TOKEN 헤더로 전송해야 합니다.")
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
                schema = @Schema(implementation = PortfolioPriceHistoryResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "시작일이 종료일보다 늦음: INVALID_PORTFOLIO_PERIOD. 날짜 형식 오류: INVALID_REQUEST. 필수 쿼리 파라미터 누락은 ProblemDetail 형식",
        content = {
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)),
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))
        }),
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
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/performance/prepare")
  public PortfolioPriceHistoryResponse preparePerformance(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "조회 시작일(포함). yyyy-MM-dd 형식이며 to 이하", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "조회 종료일(포함). yyyy-MM-dd 형식", example = "2026-10-23")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(description = "이전 준비 작업 실패 시 재시도 여부. 진행 중인 작업은 중복 실행하지 않음", example = "false")
          @RequestParam(defaultValue = "false")
          boolean retry) {
    return PortfolioPriceHistoryResponse.from(
        priceHistoryService.prepare(member.memberId(), from, to, retry));
  }

  @Operation(
      summary = "보유자산 변경 이력 조회",
      description =
          "로그인 회원의 access_token 쿠키가 필요합니다. 적용일(effectiveDate)이 from~to(양끝 포함)에 속하는 거래·잔고 변경 이력을 조회합니다. 적용일, 생성 시각, 이력 ID 내림차순이며, 해당 기간의 이력이 없으면 빈 배열입니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "처리 성공",
        content =
            @Content(
                mediaType = "application/json",
                array =
                    @ArraySchema(
                        schema = @Schema(implementation = PortfolioHoldingHistoryResponse.class)))),
    @ApiResponse(
        responseCode = "400",
        description =
            "시작일이 종료일보다 늦음: INVALID_PORTFOLIO_PERIOD. 날짜 형식 오류: INVALID_REQUEST. 필수 쿼리 파라미터 누락은 ProblemDetail 형식",
        content = {
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)),
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))
        }),
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요 또는 유효하지 않은 인증 정보: UNAUTHORIZED, INVALID_TOKEN",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "포트폴리오 없음: PORTFOLIO_NOT_FOUND",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/histories")
  public List<PortfolioHoldingHistoryResponse> histories(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "조회 시작일(포함). yyyy-MM-dd 형식이며 to 이하", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "조회 종료일(포함). yyyy-MM-dd 형식", example = "2026-10-23")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return savedPortfolioService.histories(member.memberId(), from, to);
  }
}
