package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingHistoryResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingCreateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioResponse;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.SavedPortfolioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Saved Portfolio", description = "로그인 회원의 포트폴리오와 수익률 추적 API")
@RequiredArgsConstructor
@RequestMapping({"/api/portfolio", "/api/users/me/portfolio"})
@RestController
public class SavedPortfolioController {

  private final SavedPortfolioService savedPortfolioService;
  private final PortfolioSnapshotService snapshotService;

  @Operation(summary = "내 포트폴리오 조회")
  @GetMapping
  public SavedPortfolioResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return savedPortfolioService.get(member.memberId());
  }

  @Operation(summary = "보유자산 추가")
  @PostMapping("/holdings")
  public ResponseEntity<SavedPortfolioHoldingResponse> add(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody SavedPortfolioHoldingCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(savedPortfolioService.add(member.memberId(), request));
  }

  @Operation(summary = "보유자산 수정")
  @PatchMapping("/holdings/{assetId}")
  public SavedPortfolioHoldingResponse update(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long assetId,
      @Valid @RequestBody SavedPortfolioHoldingUpdateRequest request) {
    return savedPortfolioService.update(member.memberId(), assetId, request);
  }

  @Operation(summary = "보유자산 삭제")
  @DeleteMapping("/holdings/{assetId}")
  public ResponseEntity<Void> remove(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long assetId) {
    savedPortfolioService.remove(member.memberId(), assetId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "기간별 포트폴리오 수익률 조회",
      description = "거래일별 매입금액과 평가금액 스냅샷을 조회합니다. 오늘 값은 현재 보유자산과 최근 종가로 계산하며 저장하지 않습니다.")
  @GetMapping("/performance")
  public PortfolioPerformanceResponse performance(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return snapshotService.performance(member.memberId(), from, to);
  }

  @Operation(summary = "보유자산 변경 이력 조회")
  @GetMapping("/histories")
  public List<PortfolioHoldingHistoryResponse> histories(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return savedPortfolioService.histories(member.memberId(), from, to);
  }
}
