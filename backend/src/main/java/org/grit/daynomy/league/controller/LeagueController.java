package org.grit.daynomy.league.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.dto.LeagueDto.DailyHistoryResponse;
import org.grit.daynomy.league.dto.LeagueDto.InvestorDetailResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicInvestorResponse;
import org.grit.daynomy.league.dto.LeagueDto.RankingResponse;
import org.grit.daynomy.league.dto.LeagueDto.WeeksResponse;
import org.grit.daynomy.league.service.LeagueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Investment League", description = "공개 투자 리그 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/league")
@RestController
public class LeagueController {

  private final LeagueService leagueService;

  @Operation(summary = "리그 주차 조회")
  @GetMapping("/weeks")
  public WeeksResponse weeks() {
    return leagueService.weeks();
  }

  @Operation(summary = "주간 투자 리그 조회")
  @GetMapping("/rankings")
  public RankingResponse rankings(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart,
      @RequestParam(defaultValue = "WEEKLY_RETURN") LeagueType leagueType,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return leagueService.rankings(
        member == null ? null : member.memberId(), weekStart, leagueType, page, size);
  }

  @Operation(summary = "공개 투자자 상세 조회")
  @GetMapping("/investors/{publicId}")
  public PublicInvestorResponse investor(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String publicId) {
    return leagueService.publicInvestor(member == null ? null : member.memberId(), publicId);
  }

  @Operation(summary = "공개 종목·투자 판단·복기 조회", description = "로그인이나 구독 없이 공개된 상세 기록을 조회합니다.")
  @GetMapping("/investors/{publicId}/details")
  public ResponseEntity<InvestorDetailResponse> details(@PathVariable String publicId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(leagueService.investorDetail(publicId));
  }

  @Operation(
      summary = "공개 투자자의 일별·주간 누적 수익률 조회",
      description = "최근 8주를 월요일~일요일(KST)로 구분합니다. 금액과 수량은 공개하지 않습니다.")
  @GetMapping("/investors/{publicId}/daily-history")
  public ResponseEntity<DailyHistoryResponse> dailyHistory(
      @PathVariable String publicId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(leagueService.dailyHistory(publicId, weekStart));
  }
}
