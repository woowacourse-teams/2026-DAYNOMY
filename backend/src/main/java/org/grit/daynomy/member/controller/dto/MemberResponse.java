package org.grit.daynomy.member.controller.dto;

import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;

public record MemberResponse(Long id, String email, String nickname, MemberRole role) {

  public static MemberResponse from(Member member) {
    return new MemberResponse(
        member.getId(), member.getEmail(), member.getNickname(), member.getRole());
  }
}
