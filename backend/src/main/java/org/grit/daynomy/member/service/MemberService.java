package org.grit.daynomy.member.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.CommonErrorCode;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.member.controller.dto.MemberResponse;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MemberService {

  private final MemberRepository memberRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final InvestorProfileRepository investorProfileRepository;

  @Transactional
  public Member findOrCreateGoogleMember(
      String providerId, String email, String googleName, String profileImageUrl) {
    var existing = memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, providerId);
    if (existing.isPresent()) {
      Member member = validateActiveMember(existing.get());
      member.updateGoogleName(googleName);
      return member;
    }

    for (int attempt = 0; attempt < 3; attempt++) {
      String initialNickname =
          "회원_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      Member candidate =
          Member.createGoogleMember(providerId, email, initialNickname, profileImageUrl);
      candidate.updateGoogleName(googleName);
      // Google 이름은 비공개로 저장하고, 공개 닉네임은 개인정보에서 생성하지 않는다.
      memberRepository.insertGoogleMemberIfAbsent(
          providerId, email, candidate.getGoogleName(), initialNickname, profileImageUrl);
      var created = memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, providerId);
      if (created.isPresent()) {
        return validateActiveMember(created.get());
      }
    }
    throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  public Member getMember(Long memberId) {
    return findActiveMember(memberId);
  }

  public MemberResponse getProfile(Long memberId) {
    Member member = findActiveMember(memberId);
    // 기존 공개 프로필의 사용자 선택을 보존한다. 조회 중 회원 정보를 변경하지 않는다.
    String nickname =
        investorProfileRepository
            .findByMemberId(memberId)
            .map(profile -> profile.getDisplayName())
            .orElse(member.getNickname());
    return MemberResponse.from(member, nickname);
  }

  @Transactional
  public Member updateNickname(Long memberId, String nickname) {
    Member member =
        validateActiveMember(
            memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)));
    String normalizedNickname = Member.normalizeNickname(nickname);

    if ((!member.getNickname().equals(normalizedNickname)
            && memberRepository.existsByNickname(normalizedNickname))
        || investorProfileRepository.existsByDisplayNameAndMemberIdNot(
            normalizedNickname, memberId)) {
      throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    member.updateNickname(normalizedNickname);
    investorProfileRepository
        .findByMemberId(memberId)
        .ifPresent(profile -> profile.changeDisplayName(normalizedNickname));
    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && ("uk_members_nickname".equals(violation.getConstraintName())
                || "uk_public_investor_profiles_display_name"
                    .equals(violation.getConstraintName()))) {
          throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
        }
      }
      throw exception;
    }
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
