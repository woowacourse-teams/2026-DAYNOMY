package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiResponses({
  @ApiResponse(responseCode = "200", description = "요청 성공", useReturnTypeSchema = true),
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: 로그인 필요, INVALID_TOKEN: 인증 정보가 유효하지 않음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@Tag(name = "PortfolioAnalysis", description = "포트폴리오 분석 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/portfolio/analysis")
public class SavedPortfolioAnalysisController {

  private final SavedPortfolioAnalysisService service;
  private final SavedPortfolioAnalysisTaskService tasks;

  @Operation(
      summary = "내 포트폴리오 백그라운드 분석 시작 또는 재사용",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /api/auth/csrf 응답의 token. 발급된 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      },
      description =
          "쿠키 인증과 CSRF 검증이 필요합니다. 분석을 서버에서 실행하고 즉시 상태를 반환합니다. 진행 중인 작업이나 유효한 저장 결과가 있으면 재사용합니다. 새로고침·화면 이동 후에도 GET /api/users/me/portfolio/analysis로 진행 상태와 결과를 조회합니다. 서버 재시작 시 자동 재개는 보장하지 않으며 기존 5분 요청 유효 시간이 적용됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "분석 진행 중", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "503",
        description = "PORTFOLIO_ANALYSIS_BUSY: 동시 실행 한도 초과. 작업은 시작되지 않으며 재시도 가능",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/tasks")
  public ResponseEntity<SavedPortfolioAnalysisResponse> start(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return taskResponse(tasks.start(member.memberId(), false));
  }

  @Operation(
      summary = "내 포트폴리오 새 근거 확인을 백그라운드로 시작",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /api/auth/csrf 응답의 token. 발급된 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      },
      description =
          "쿠키 인증과 CSRF 검증이 필요합니다. 기존 새 근거 확인과 같은 분석·검증을 서버에서 수행합니다. 진행 중인 작업이 있으면 추가 실행하지 않습니다. GET /api/users/me/portfolio/analysis로 상태와 결과를 조회하며 실패해도 이전 저장 분석은 보존합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "분석 진행 중", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "503",
        description = "PORTFOLIO_ANALYSIS_BUSY: 동시 실행 한도 초과",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/tasks/refresh")
  public ResponseEntity<SavedPortfolioAnalysisResponse> startRefresh(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return taskResponse(tasks.start(member.memberId(), true));
  }

  private ResponseEntity<SavedPortfolioAnalysisResponse> taskResponse(
      SavedPortfolioAnalysisResponse result) {
    if (result.status() == SavedPortfolioAnalysisResponse.Status.PROCESSING) {
      return ResponseEntity.accepted()
          .location(URI.create("/api/users/me/portfolio/analysis"))
          .body(result);
    }
    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "내 포트폴리오 분석 변경 이력 조회",
      description =
          "access_token 쿠키 인증이 필요합니다. 본인의 최근 분석 이력 최대 10개를 최신순으로 반환합니다. 삭제된 오래된 이력을 가리키는 previousAnalysisId는 null입니다. 저장 이력이 없으면 빈 배열을 반환합니다.")
  @GetMapping("/history")
  public java.util.List<org.grit.daynomy.portfolio.dto.PortfolioAnalysisHistoryResponse> history(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.history(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 저장 분석 조회",
      description =
          "access_token 쿠키 인증이 필요합니다. AI를 호출하지 않습니다. 자산 변경·진행 중·최근 실패 상태에서도 이전 분석을 반환합니다. 분석이 없으면 analysis는 null이며, 실패·진행 중 상태가 아니면 EMPTY입니다.")
  @GetMapping
  public SavedPortfolioAnalysisResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.get(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 새 근거 확인",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /api/auth/csrf 응답의 token. 발급된 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      },
      description =
          "access_token 쿠키 인증이 필요합니다. 실제 웹 검색을 실행합니다. 문체·출처 오류는 최대 한 번 교정하며, 일부 종목의 검증 실패 시 유효한 종목과 기존 근거를 보존합니다. 유효한 동일 근거이면 기존 분석과 생성 시각을 유지하고 최근 확인 시각만 갱신합니다. 이전·현재 모두 근거가 없거나 새 사건으로 확인하기 어려우면 RECHECK_REQUIRED, 검색 실패 시 오류를 반환하며 이전 분석을 보존합니다. 동시 요청은 409입니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "403",
        description = "FORBIDDEN: CSRF 쿠키 또는 헤더 토큰 누락·불일치",
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
  @PostMapping("/refresh")
  public SavedPortfolioAnalysisResponse refresh(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.refresh(member.memberId());
  }

  @Operation(
      summary = "내 포트폴리오 분석 생성 또는 재사용",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /api/auth/csrf 응답의 token. 발급된 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      },
      description =
          "access_token 쿠키 인증이 필요합니다. DB의 보유 자산과 가격으로 분석합니다. 종목별 유효 분석은 사용자 간 재사용하고 없거나 만료된 종목만 검색합니다. 자산·비중·분석 기준이 같으면 기존 결과를 반환하며 확인 성공 시각은 갱신하지 않습니다. 새 뉴스 확인과는 별개입니다. 동시 분석은 409를 반환합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "403",
        description = "FORBIDDEN: CSRF 쿠키 또는 헤더 토큰 누락·불일치",
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
  public SavedPortfolioAnalysisResponse analyze(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.analyze(member.memberId(), null);
  }
}
