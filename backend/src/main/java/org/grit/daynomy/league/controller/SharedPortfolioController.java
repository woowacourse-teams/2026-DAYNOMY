package org.grit.daynomy.league.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.league.dto.SharedPortfolioDto.*;
import org.grit.daynomy.league.service.SharedPortfolioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "My Investment League", description = "로그인 회원의 투자 리그 관리 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users/me/shared-portfolio")
public class SharedPortfolioController {
  private final SharedPortfolioService service;

  @Operation(
      summary = "내 공유 포트폴리오 조회",
      description = "투자 리그의 공유 포트폴리오 보유 종목과 최근 수집 종가 기준 평가 결과를 조회합니다.")
  @GetMapping
  public ResponseEntity<PortfolioResponse> get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return response(service.getMine(member.memberId()));
  }

  @Operation(
      summary = "내 공유 포트폴리오 종목 가져오기",
      description =
          "선택한 보유 종목의 수량과 평균 매수가를 공유 포트폴리오에 저장합니다. "
              + "기존 종목은 overwriteExisting이 true인 경우에만 갱신합니다.")
  @PostMapping("/import")
  public ResponseEntity<PortfolioResponse> importHoldings(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ImportRequest request) {
    return response(service.importHoldings(member.memberId(), request));
  }

  @Operation(
      summary = "내 공유 포트폴리오 종목 공개 설정",
      description = "공유 포트폴리오 종목의 숨김 여부를 변경합니다. hidden이 true이면 공개 종목 목록에서 숨깁니다.")
  @PatchMapping("/holdings/{assetId}/visibility")
  public ResponseEntity<PortfolioResponse> changeVisibility(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long assetId,
      @Valid @RequestBody VisibilityRequest request) {
    return response(service.changeVisibility(member.memberId(), assetId, request));
  }

  @Operation(
      summary = "내 공유 포트폴리오 종목 삭제",
      description = "공유 포트폴리오에서 해당 종목을 삭제합니다. 마지막 종목을 삭제하면 리그 참여와 공개 설정을 해제합니다.")
  @DeleteMapping("/holdings/{assetId}")
  public ResponseEntity<PortfolioResponse> removeHolding(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long assetId) {
    return response(service.removeHolding(member.memberId(), assetId));
  }

  private ResponseEntity<PortfolioResponse> response(PortfolioResponse value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
