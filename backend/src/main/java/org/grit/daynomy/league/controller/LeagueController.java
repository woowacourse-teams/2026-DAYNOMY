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

  @Operation(summary = "리그 주차 조회", description = "이번 주부터 최근 3주를 최신순으로 반환합니다. 주차는 KST 월요일 기준입니다.")
  @GetMapping("/weeks")
  public WeeksResponse weeks() {
    return leagueService.weeks();
  }

  @Operation(summary = "주간 투자 리그 조회")
  @GetMapping("/rankings")
  public ResponseEntity<RankingResponse> rankings(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Parameter(description = "최근 3주 중 조회할 주의 KST 월요일(yyyy-MM-dd). 생략하면 이번 주")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart,
      @RequestParam(defaultValue = "WEEKLY_RETURN") LeagueType leagueType,
      @Parameter(description = "페이지 번호(0부터 시작)") @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            leagueService.rankings(
                member == null ? null : member.memberId(), weekStart, leagueType, page, size));
  }

  @Operation(summary = "공개 투자자 상세 조회")
  @GetMapping("/investors/{publicId}")
  public ResponseEntity<PublicInvestorResponse> investor(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String publicId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(leagueService.publicInvestor(member == null ? null : member.memberId(), publicId));
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
      description =
          "최근 3주를 월요일~일요일 7일로 반환합니다. 휴장일은 하루 0%·누적 유지, 수집 지연은 대기로 표시합니다. 날짜는 시장별 거래일이며 미국 금요일 종가는 토요일 수집 후 금요일에 반영됩니다. 금액과 수량은 공개하지 않습니다.")
  @GetMapping("/investors/{publicId}/daily-history")
  public ResponseEntity<DailyHistoryResponse> dailyHistory(
      @PathVariable String publicId,
      @Parameter(description = "최근 3주 중 조회할 주의 KST 월요일(yyyy-MM-dd). 생략하면 이번 주")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(leagueService.dailyHistory(publicId, weekStart));
  }
}
