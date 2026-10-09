package org.grit.daynomy.league.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Investment League", description = "관리자 투자 리그 집계 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/league")
@RestController
public class AdminLeagueController {

  private final LeagueReturnService leagueReturnService;

  @Operation(summary = "리그 수익률 재집계")
  @PostMapping("/recalculate")
  public RecalculationResponse recalculate() {
    return new RecalculationResponse(leagueReturnService.recalculate());
  }

  public record RecalculationResponse(int calculatedCount) {}
}
