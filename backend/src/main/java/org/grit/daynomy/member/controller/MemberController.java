package org.grit.daynomy.member.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.global.response.ApiResponse;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.controller.dto.MemberResponse;
import org.grit.daynomy.member.controller.dto.MemberUpdateRequest;
import org.grit.daynomy.member.service.MemberService;
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
  public ApiResponse<MemberResponse> getMe(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    Member member = memberService.getMember(authenticatedMember.memberId());

    return ApiResponse.success("회원 정보를 조회했습니다.", MemberResponse.from(member));
  }

  @PatchMapping("/me")
  public ApiResponse<MemberResponse> updateMe(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Valid @RequestBody MemberUpdateRequest request) {
    Member member =
        memberService.updateNickname(authenticatedMember.memberId(), request.nickname());

    return ApiResponse.success("회원 정보가 수정되었습니다.", MemberResponse.from(member));
  }

  @DeleteMapping("/me")
  public ApiResponse<Void> withdraw(
      @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      HttpServletResponse response) {
    memberService.withdraw(authenticatedMember.memberId());
    tokenCookieManager.clearTokenCookies(response);

    return ApiResponse.success("회원 탈퇴가 완료되었습니다.", null);
  }
}
