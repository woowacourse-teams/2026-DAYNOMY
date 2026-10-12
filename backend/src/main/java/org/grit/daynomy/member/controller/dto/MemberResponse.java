package org.grit.daynomy.member.controller.dto;

import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;

public record MemberResponse(Long id, String email, String name, String nickname, MemberRole role) {

  public static MemberResponse from(Member member) {
    return from(member, member.getNickname());
  }

  public static MemberResponse from(Member member, String nickname) {
    return new MemberResponse(
        member.getId(), member.getEmail(), member.getGoogleName(), nickname, member.getRole());
  }
}
