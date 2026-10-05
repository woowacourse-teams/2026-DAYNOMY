package org.grit.daynomy.investmentcalendar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.service.InvestmentCalendarService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Investment Calendar", description = "로그인 회원의 투자 일정과 포트폴리오 영향 분석 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/users/me/investment-calendar")
@RestController
public class InvestmentCalendarController {

  private final InvestmentCalendarService investmentCalendarService;

  @Operation(summary = "월별 투자 캘린더 조회")
  @GetMapping
  public InvestmentCalendarResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam @Min(2000) @Max(2100) int year,
      @RequestParam @Min(1) @Max(12) int month) {
    return investmentCalendarService.get(member.memberId(), YearMonth.of(year, month));
  }
}
