package org.grit.daynomy.investmentcapacity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanRequest;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanResponse;
import org.grit.daynomy.investmentcapacity.service.InvestmentPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Investment Plan", description = "로그인 회원의 금융 실험실 계획 API")
@RequiredArgsConstructor
@RequestMapping("/api/users/me/investment-plan")
@RestController
public class InvestmentPlanController {

  private final InvestmentPlanService investmentPlanService;

  @Operation(summary = "내 금융 계획 조회", description = "저장된 재무상태와 목표 계획을 조회합니다.")
  @GetMapping
  public ResponseEntity<InvestmentPlanResponse> get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    InvestmentPlanResponse response = investmentPlanService.get(member.memberId());
    return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
  }

  @Operation(summary = "금융 계획 저장", description = "재무상태와 목표를 저장하고 전략·상품 추천을 계산합니다.")
  @PutMapping
  public InvestmentPlanResponse save(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody InvestmentPlanRequest request) {
    return investmentPlanService.save(member.memberId(), request);
  }
}
