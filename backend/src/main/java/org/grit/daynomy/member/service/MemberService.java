package org.grit.daynomy.member.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MemberService {

  private final MemberRepository memberRepository;
  private final RefreshTokenRepository refreshTokenRepository;

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

  public Member getMember(Long memberId) {
    return findActiveMember(memberId);
  }

  @Transactional
  public Member updateNickname(Long memberId, String nickname) {
    Member member = findActiveMember(memberId);
    String normalizedNickname = nickname.trim();

    if (!member.getNickname().equals(normalizedNickname)
        && memberRepository.existsByNickname(normalizedNickname)) {
      throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    member.updateNickname(normalizedNickname);
    return member;
  }

  @Transactional
  public void withdraw(Long memberId) {
    Member member = findActiveMember(memberId);

    refreshTokenRepository.deleteAllByMember_Id(memberId);
    member.withdraw();
  }

  private Member findActiveMember(Long memberId) {
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    return validateActiveMember(member);
  }

  private Member validateActiveMember(Member member) {
    if (member.isWithdrawn()) {
      throw new BusinessException(MemberErrorCode.WITHDRAWN_MEMBER);
    }

    return member;
  }
}
