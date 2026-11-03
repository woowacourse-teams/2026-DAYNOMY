package org.grit.daynomy.investmentcalendar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.dto.GuestInvestmentCalendarRequest;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.service.InvestmentCalendarService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Investment Calendar", description = "투자 일정과 포트폴리오 영향 분석 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/investment-calendar")
@RestController
public class PublicInvestmentCalendarController {

  private final InvestmentCalendarService investmentCalendarService;

  @Operation(
      summary = "비회원 월별 투자 캘린더 조회",
      description =
          "한국 시간 기준 해당 월의 일정을 발표 시각 오름차순으로 반환합니다. 일정이 없으면 events는 빈 배열입니다. 요청한 보유자산으로 분석합니다. 로그인은 필요 없지만 CSRF 쿠키와 헤더는 필요합니다.",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /api/auth/csrf 응답의 token. 발급된 XSRF-TOKEN 쿠키도 함께 전송",
            schema = @Schema(type = "string"))
      })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: 필수 파라미터 누락, 타입 또는 입력 범위 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "FORBIDDEN: CSRF 쿠키 또는 헤더 토큰 누락·불일치",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public InvestmentCalendarResponse get(
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
          InvestmentCalendarScope scope,
      @Valid @RequestBody GuestInvestmentCalendarRequest request) {
    return investmentCalendarService.getForGuest(
        YearMonth.of(year, month),
        scope,
        request.holdings().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    holding -> holding.assetId(),
                    holding -> holding.quantity(),
                    (first, ignored) -> first)));
  }
}
