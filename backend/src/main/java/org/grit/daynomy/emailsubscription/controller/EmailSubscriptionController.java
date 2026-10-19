package org.grit.daynomy.emailsubscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.emailsubscription.dto.EmailSubscriptionResponse;
import org.grit.daynomy.emailsubscription.service.EmailSubscriptionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Email Subscription", description = "로그인 Google 이메일의 구독 상태 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/email-subscription")
public class EmailSubscriptionController {

  private final EmailSubscriptionService service;

  @Operation(summary = "이메일 구독 상태 및 최초 안내 여부 조회")
  @GetMapping
  public EmailSubscriptionResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.get(member.memberId());
  }

  @Operation(summary = "이메일 구독 및 재구독", description = "요청 본문 없이 로그인한 Google 이메일로 구독합니다.")
  @PutMapping
  public EmailSubscriptionResponse subscribe(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.subscribe(member.memberId());
  }

  @Operation(
      summary = "이메일 구독 안내 건너뛰기",
      description = "최초 미결정 상태만 미구독으로 변경합니다. 이미 구독·거절·해지한 상태는 유지합니다.")
  @PostMapping("/dismiss")
  public EmailSubscriptionResponse dismiss(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.dismiss(member.memberId());
  }

  @Operation(summary = "이메일 구독 해지", description = "구독 중인 상태만 해지합니다. 그 외 상태는 유지합니다.")
  @DeleteMapping
  public EmailSubscriptionResponse unsubscribe(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return service.unsubscribe(member.memberId());
  }
}
