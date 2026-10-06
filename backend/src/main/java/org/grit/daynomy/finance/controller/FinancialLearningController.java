package org.grit.daynomy.finance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeCreateRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressUpdateRequest;
import org.grit.daynomy.finance.service.FinancialLearningService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Financial Learning", description = "로그인 회원의 금융 학습 학습·루틴·모의투자 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/users/me/learning")
@RestController
public class FinancialLearningController {

  private final FinancialLearningService learningService;

  @Operation(summary = "금융 학습 진행 상태 조회")
  @GetMapping("/progress")
  public ProgressListResponse progress(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return learningService.progress(member.memberId());
  }

  @Operation(summary = "금융 학습 진행 상태 저장")
  @PutMapping("/progress/{itemKey}")
  public ProgressResponse updateProgress(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable @Size(min = 1, max = 100) String itemKey,
      @Valid @RequestBody ProgressUpdateRequest request) {
    return learningService.updateProgress(member.memberId(), itemKey, request);
  }

  @Operation(summary = "주간 금융 체크인 조회")
  @GetMapping("/check-ins")
  public CheckInListResponse checkIns(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(defaultValue = "12") @Min(1) @Max(52) int limit) {
    return learningService.checkIns(member.memberId(), limit);
  }

  @Operation(summary = "주간 금융 체크인 저장")
  @PutMapping("/check-ins/{weekStart}")
  public CheckInResponse saveCheckIn(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @Valid @RequestBody CheckInRequest request) {
    return learningService.saveCheckIn(member.memberId(), weekStart, request);
  }

  @Operation(summary = "모의거래 목록 조회")
  @GetMapping("/mock-trades")
  public MockTradeListResponse mockTrades(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return learningService.mockTrades(member.memberId());
  }

  @Operation(summary = "모의거래 추가")
  @PostMapping("/mock-trades")
  public ResponseEntity<MockTradeResponse> addMockTrade(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody MockTradeCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(learningService.addMockTrade(member.memberId(), request));
  }

  @Operation(summary = "모의거래 삭제")
  @DeleteMapping("/mock-trades/{tradeId}")
  public ResponseEntity<Void> removeMockTrade(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long tradeId) {
    learningService.removeMockTrade(member.memberId(), tradeId);
    return ResponseEntity.noContent().build();
  }
}
