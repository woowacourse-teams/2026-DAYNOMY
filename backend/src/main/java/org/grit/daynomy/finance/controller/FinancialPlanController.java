package org.grit.daynomy.finance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.finance.dto.FinancialPlanDto.*;
import org.grit.daynomy.finance.service.FinancialPlanService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users/me/financial-plans")
public class FinancialPlanController {
  private final FinancialPlanService service;

  @GetMapping
  public ResponseEntity<PlanListResponse> list(
      @AuthenticationPrincipal AuthenticatedMember member) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.list(member.memberId()));
  }

  @PutMapping("/{key}")
  public ResponseEntity<PlanResponse> save(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String key,
      @Valid @RequestBody PlanRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.save(member.memberId(), key, request));
  }

  @DeleteMapping("/{key}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String key) {
    service.remove(member.memberId(), key);
    return ResponseEntity.noContent().build();
  }
}
