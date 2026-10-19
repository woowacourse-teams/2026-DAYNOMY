package org.grit.daynomy.investmentcalendar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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

  @Operation(summary = "비회원 월별 투자 캘린더 조회")
  @PostMapping
  public InvestmentCalendarResponse get(
      @RequestParam @Min(2000) @Max(2100) int year,
      @RequestParam @Min(1) @Max(12) int month,
      @RequestParam(defaultValue = "PORTFOLIO") InvestmentCalendarScope scope,
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
