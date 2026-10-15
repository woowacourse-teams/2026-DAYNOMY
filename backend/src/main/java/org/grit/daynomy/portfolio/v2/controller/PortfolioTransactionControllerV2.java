package org.grit.daynomy.portfolio.v2.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.v2.service.PortfolioTransactionServiceV2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio V2", description = "거래 등록과 과거 스냅샷 일괄 조회")
@RequiredArgsConstructor
@RequestMapping("/api/v2/users/me/portfolio/transactions")
@RestController
public class PortfolioTransactionControllerV2 {

  private final PortfolioTransactionServiceV2 service;

  @Operation(summary = "매수·매도 거래 등록 V2", description = "거래 저장·보유자산·과거 스냅샷 갱신을 한 트랜잭션으로 처리합니다.")
  @PostMapping
  public ResponseEntity<PortfolioTransactionResponse> transact(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioTransactionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.transact(member.memberId(), request));
  }
}
