package org.grit.daynomy.league.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.league.dto.SharedPortfolioDto.*;
import org.grit.daynomy.league.service.SharedPortfolioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users/me/shared-portfolio")
public class SharedPortfolioController {
  private final SharedPortfolioService service;

  @GetMapping
  public ResponseEntity<PortfolioResponse> get(
      @AuthenticationPrincipal AuthenticatedMember member) {
    return response(service.getMine(member.memberId()));
  }

  @PostMapping("/import")
  public ResponseEntity<PortfolioResponse> importHoldings(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ImportRequest request) {
    return response(service.importHoldings(member.memberId(), request));
  }

  @PatchMapping("/holdings/{assetId}/visibility")
  public ResponseEntity<PortfolioResponse> changeVisibility(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long assetId,
      @Valid @RequestBody VisibilityRequest request) {
    return response(service.changeVisibility(member.memberId(), assetId, request));
  }

  private ResponseEntity<PortfolioResponse> response(PortfolioResponse value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
