package org.grit.daynomy.member.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MemberService {

  private final MemberRepository memberRepository;

  @Transactional
  public Member findOrCreateGoogleMember(
      String providerId, String email, String nickname, String profileImageUrl) {
    return memberRepository
        .findByProviderAndProviderId(OAuthProvider.GOOGLE, providerId)
        .map(this::validateActiveMember)
        .orElseGet(
            () ->
                memberRepository.save(
                    Member.createGoogleMember(providerId, email, nickname, profileImageUrl)));
  }

  private Member validateActiveMember(Member member) {
    if (member.isWithdrawn()) {
      throw new IllegalStateException("탈퇴한 회원입니다.");
    }

    return member;
  }
}
