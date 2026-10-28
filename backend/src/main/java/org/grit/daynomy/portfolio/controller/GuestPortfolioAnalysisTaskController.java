package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.GuestPortfolioAnalysisTaskResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortfolioAnalysis", description = "포트폴리오 분석 API")
@ApiResponses({
  @ApiResponse(responseCode = "200", description = "조회·기존 작업 재사용 성공", useReturnTypeSchema = true),
  @ApiResponse(
      responseCode = "400",
      description = "입력 검증 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @ApiResponse(
      responseCode = "403",
      description = "체험 소진·재시도 불가·로그인 상태에서 비회원 분석 실행",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @ApiResponse(
      responseCode = "404",
      description = "PORTFOLIO_ANALYSIS_TASK_NOT_FOUND: 같은 세션의 작업이 아님",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @ApiResponse(
      responseCode = "409",
      description = "PORTFOLIO_ANALYSIS_SESSION_REQUIRED: 먼저 GET으로 세션 준비 필요",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @ApiResponse(
      responseCode = "503",
      description = "PORTFOLIO_ANALYSIS_BUSY: 실행 한도 초과. 조회 후 명시적 재시도 가능",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolio/analysis/tasks")
public class GuestPortfolioAnalysisTaskController {

  private final GuestPortfolioAnalysisTaskService service;

  @Operation(
      summary = "비회원 분석 세션 준비 및 기존 작업 복원",
      description =
          "분석 시작 전에 먼저 호출해 세션 쿠키를 받아야 합니다. 이후 모든 요청에 같은 쿠키를 전달하세요. 작업이 없으면 EMPTY를 반환하고 체험을 소진하지 않습니다. 새로고침 시 이 조회로 작업 ID와 결과를 복원합니다. AI를 호출하지 않습니다. 서버 재시작·세션 만료 후 복구는 보장하지 않습니다.")
  @GetMapping
  public GuestPortfolioAnalysisTaskResponse current() {
    return service.get();
  }

  @Operation(
      summary = "비회원 백그라운드 분석 시작 또는 기존 작업 재사용",
      description =
          "비회원 전용입니다. GET /api/portfolio/analysis/tasks에서 받은 세션 쿠키가 필요하며, 유효한 세션 쿠키가 없으면 409입니다. 종목·비중이 같은 진행 중·완료·실패 작업은 그대로 반환하며 추가 실행하지 않습니다. 다른 입력이나 기존 동기 API로 체험을 소진했다면 403입니다. 빈 자산은 체험 소진 없이 빈 결과를 반환합니다. 핵심 결과가 준비되면 종합 설명 작성 중에도 조회할 수 있습니다. 같은 세션의 동기 API와도 체험을 공유합니다.")
  @ApiResponse(
      responseCode = "202",
      description = "분석 진행 중. Location으로 상태 조회",
      useReturnTypeSchema = true)
  @PostMapping
  public ResponseEntity<GuestPortfolioAnalysisTaskResponse> start(
      @Valid @RequestBody PortfolioAnalysisRequest request, HttpServletRequest http) {
    if (!request.assets().isEmpty()) {
      requireSession(http);
    }
    return response(service.start(request));
  }

  @Operation(
      summary = "비회원 분석 작업 조회",
      description = "같은 세션의 작업만 조회할 수 있습니다. 작업 ID가 없거나 다른 세션의 작업이면 404입니다. AI를 호출하지 않습니다.")
  @GetMapping("/{id}")
  public GuestPortfolioAnalysisTaskResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @Operation(
      summary = "비회원 분석 실패 작업 명시적 재시도",
      description =
          "비회원 전용입니다. 같은 세션 쿠키가 필요합니다. retryAllowed=true인 핵심 분석 실패에 한해 원래 입력으로 1회 재시도합니다. 실행 한도 초과로 시작하지 못한 작업은 횟수를 차감하지 않습니다. 진행 중이면 재사용하며, 종합 설명만 실패했거나 작업이 만료됐다면 재실행하지 않습니다. 새로고침 시 이 API를 자동 호출하면 안 됩니다.")
  @PostMapping("/{id}/retry")
  @ApiResponse(
      responseCode = "202",
      description = "분석 진행 중. Location으로 상태 조회",
      useReturnTypeSchema = true)
  public ResponseEntity<GuestPortfolioAnalysisTaskResponse> retry(
      @PathVariable UUID id, HttpServletRequest http) {
    requireSession(http);
    return response(service.retry(id));
  }

  private ResponseEntity<GuestPortfolioAnalysisTaskResponse> response(
      GuestPortfolioAnalysisTaskResponse result) {
    if ("PROCESSING".equals(result.status())) {
      return ResponseEntity.accepted()
          .location(URI.create("/api/portfolio/analysis/tasks/" + result.taskId()))
          .body(result);
    }
    return ResponseEntity.ok(result);
  }

  private void requireSession(HttpServletRequest request) {
    if (!request.isRequestedSessionIdValid()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SESSION_REQUIRED);
    }
  }
}
