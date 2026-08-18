package org.grit.daynomy.member.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.member.controller.dto.MemberResponse;
import org.grit.daynomy.member.controller.dto.MemberUpdateRequest;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/users")
@RestController
public class MemberController {

  private final MemberService memberService;
  private final TokenCookieManager tokenCookieManager;

  @GetMapping("/me")
  public ResponseEntity<MemberResponse> getMe(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    Member member = memberService.getMember(authenticatedMember.memberId());

    return ResponseEntity.ok(MemberResponse.from(member));
  }

  @PatchMapping("/me")
  public ResponseEntity<MemberResponse> updateMe(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Valid @RequestBody MemberUpdateRequest request) {
    Member member =
        memberService.updateNickname(authenticatedMember.memberId(), request.nickname());

    return ResponseEntity.ok(MemberResponse.from(member));
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      HttpServletResponse response) {
    memberService.withdraw(authenticatedMember.memberId());
    tokenCookieManager.clearTokenCookies(response);

    return ResponseEntity.noContent().build();
  }
}
