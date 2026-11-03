package org.grit.daynomy.investmentcalendar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.service.InvestmentCalendarService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Investment Calendar", description = "투자 일정과 포트폴리오 영향 분석 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/users/me/investment-calendar")
@RestController
public class InvestmentCalendarController {

  private final InvestmentCalendarService investmentCalendarService;

  @Operation(
      summary = "월별 투자 캘린더 조회",
      description =
          "access_token 쿠키 인증이 필요합니다. 한국 시간 기준 해당 월의 일정을 발표 시각 오름차순으로 반환합니다. 일정이 없으면 events는 빈 배열입니다. 서버에 저장된 보유자산으로 분석합니다. 보유자산이 없어도 거시경제 일정은 조회할 수 있습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 필수 파라미터 누락, 타입 또는 입력 범위 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "UNAUTHORIZED: 로그인 필요, INVALID_TOKEN: 인증 정보가 유효하지 않음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public InvestmentCalendarResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "조회 연도(한국 시간 기준)", example = "2026")
          @RequestParam
          @Min(2000)
          @Max(2100)
          int year,
      @Parameter(description = "조회 월(한국 시간 기준)", example = "10") @RequestParam @Min(1) @Max(12)
          int month,
      @Parameter(
              description =
                  "PORTFOLIO: 거시경제 일정과 보유 종목의 실적 일정, ALL: 모든 일정. 영향 분석에는 두 범위 모두 보유자산을 사용")
          @RequestParam(defaultValue = "PORTFOLIO")
          InvestmentCalendarScope scope) {
    return investmentCalendarService.get(member.memberId(), YearMonth.of(year, month), scope);
  }
}
