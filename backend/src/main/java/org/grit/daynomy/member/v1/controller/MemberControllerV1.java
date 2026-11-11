package org.grit.daynomy.member.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.member.controller.dto.MemberResponse;
import org.grit.daynomy.member.controller.dto.MemberUpdateRequest;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.v1.service.MemberServiceV1;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member V1", description = "회원 정보 조회 및 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@RestController
public class MemberControllerV1 {

  private final MemberServiceV1 memberService;
  private final TokenCookieManager tokenCookieManager;

  @Operation(summary = "내 정보 조회", description = "로그인한 회원의 정보를 조회합니다.")
  @GetMapping("/me")
  public ResponseEntity<MemberResponse> getMe(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    return ResponseEntity.ok(memberService.getProfile(authenticatedMember.memberId()));
  }

  @Operation(summary = "내 정보 수정", description = "로그인한 회원의 닉네임을 수정합니다.")
  @PatchMapping("/me")
  public ResponseEntity<MemberResponse> updateMe(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Valid @RequestBody MemberUpdateRequest request) {
    Member member =
        memberService.updateNickname(authenticatedMember.memberId(), request.nickname());

    return ResponseEntity.ok(MemberResponse.from(member));
  }

  @Operation(summary = "회원 탈퇴", description = "로그인한 회원을 탈퇴 처리하고 인증 쿠키를 삭제합니다.")
  @DeleteMapping("/me")
  public ResponseEntity<Void> withdraw(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Parameter(hidden = true) HttpServletResponse response) {
    memberService.withdraw(authenticatedMember.memberId());
    tokenCookieManager.clearTokenCookies(response);

    return ResponseEntity.noContent().build();
  }
}
