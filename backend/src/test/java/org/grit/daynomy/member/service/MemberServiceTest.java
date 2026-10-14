package org.grit.daynomy.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.util.Optional;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private InvestorProfileRepository investorProfileRepository;

  @InjectMocks private MemberService memberService;

  @Test
  @DisplayName("기존 Google 회원이 있으면 해당 회원을 반환한다")
  void findOrCreateGoogleMemberReturnsExistingMember() {
    Member member = createActiveMember("기존회원");
    given(memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-id"))
        .willReturn(Optional.of(member));

    Member result =
        memberService.findOrCreateGoogleMember(
            "google-id", "member@example.com", "새닉네임", "new-image.png");

    assertThat(result).isSameAs(member);
    assertThat(result.getNickname()).isEqualTo("기존회원");
    assertThat(result.getGoogleName()).isEqualTo("새닉네임");
    verify(memberRepository, never()).save(any(Member.class));
    verify(memberRepository, never()).insertGoogleMemberIfAbsent(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("기존 Google 회원이 없으면 새로운 회원을 저장한다")
  void findOrCreateGoogleMemberCreatesMemberWhenMissing() {
    Member created = createActiveMember("회원_테스트");
    created.updateGoogleName("Google 이름");
    given(memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-id"))
        .willReturn(Optional.empty(), Optional.of(created));

    Member member =
        memberService.findOrCreateGoogleMember(
            "google-id", "member@example.com", "Google 이름", "profile.png");

    assertThat(member.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
    assertThat(member.getProviderId()).isEqualTo("google-id");
    assertThat(member.getEmail()).isEqualTo("member@example.com");
    assertThat(member.getNickname()).isEqualTo("회원_테스트");
    assertThat(member.getGoogleName()).isEqualTo("Google 이름");
    assertThat(member.getProfileImageUrl()).isEqualTo("profile.png");
    assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    verify(memberRepository)
        .insertGoogleMemberIfAbsent(
            eq("google-id"),
            eq("member@example.com"),
            eq("Google 이름"),
            argThat(value -> value.matches("회원_[0-9a-f]{16}")),
            eq("profile.png"));
  }

  @Test
  @DisplayName("기존 Google 회원이 탈퇴 상태이면 예외를 던진다")
  void findOrCreateGoogleMemberThrowsWhenExistingMemberIsWithdrawn() {
    Member member = createActiveMember("탈퇴회원");
    member.withdraw();
    given(memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-id"))
        .willReturn(Optional.of(member));

    assertThatThrownBy(
            () ->
                memberService.findOrCreateGoogleMember(
                    "google-id", "member@example.com", "daynomy", null))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
  }

  @Test
  @DisplayName("회원 ID에 해당하는 회원이 없으면 예외를 던진다")
  void getMemberThrowsWhenMemberIsMissing() {
    given(memberRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> memberService.getMember(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
  }

  @Test
  void profileUsesTheExistingPublicNicknameWithoutGuessingThePrivateGoogleNameOrWritingData() {
    Member member = createActiveMember("기존Google이름");
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));
    given(investorProfileRepository.findByMemberId(1L))
        .willReturn(
            Optional.of(
                InvestorProfile.create(
                    member, "사용자가정한별명", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED)));
    var response = memberService.getProfile(1L);
    assertThat(response.nickname()).isEqualTo("사용자가정한별명");
    assertThat(response.name()).isNull();
    assertThat(member.getNickname()).isEqualTo("기존Google이름");
    verify(memberRepository, never()).flush();
  }

  @Test
  @DisplayName("닉네임의 앞뒤 공백을 제거하고 수정한다")
  void updateNicknameNormalizesAndUpdatesNickname() {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(memberRepository.existsByNickname("새닉네임")).willReturn(false);

    Member result = memberService.updateNickname(1L, "  새닉네임  ");

    assertThat(result).isSameAs(member);
    assertThat(member.getNickname()).isEqualTo("새닉네임");
    verify(memberRepository).existsByNickname("새닉네임");
    verify(memberRepository).flush();
  }

  @Test
  @DisplayName("다른 회원이 사용 중인 닉네임이면 수정하지 않고 예외를 던진다")
  void updateNicknameThrowsWhenNicknameAlreadyExists() {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(memberRepository.existsByNickname("중복닉네임")).willReturn(true);

    assertThatThrownBy(() -> memberService.updateNickname(1L, "중복닉네임"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
    assertThat(member.getNickname()).isEqualTo("기존닉네임");
  }

  @Test
  @DisplayName("현재 닉네임과 같으면 중복 여부를 조회하지 않는다")
  void updateNicknameSkipsDuplicateCheckWhenNicknameIsUnchanged() {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

    memberService.updateNickname(1L, "  기존닉네임  ");

    assertThat(member.getNickname()).isEqualTo("기존닉네임");
    verify(memberRepository, never()).existsByNickname(any(String.class));
    verify(memberRepository).flush();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\u3000", "123456789012345678901"})
  void updateNicknameRejectsInvalidInputWithoutChangingMember(String nickname) {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> memberService.updateNickname(1L, nickname))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.INVALID_NICKNAME);
    assertThat(member.getNickname()).isEqualTo("기존닉네임");
    verify(memberRepository, never()).flush();
  }

  @Test
  void duplicateDetectedAtFlushIsReportedAsNicknameConflict() {
    given(memberRepository.findByIdForUpdate(1L))
        .willReturn(Optional.of(createActiveMember("기존닉네임")));
    doThrow(
            new DataIntegrityViolationException(
                "conflict",
                new ConstraintViolationException(
                    "conflict", new SQLException(), "uk_members_nickname")))
        .when(memberRepository)
        .flush();

    assertThatThrownBy(() -> memberService.updateNickname(1L, "동시요청닉네임"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.NICKNAME_ALREADY_EXISTS);
  }

  @Test
  void unrelatedDatabaseFailureIsNotReportedAsNicknameConflict() {
    given(memberRepository.findByIdForUpdate(1L))
        .willReturn(Optional.of(createActiveMember("기존닉네임")));
    var failure = new DataIntegrityViolationException("unrelated database failure");
    doThrow(failure).when(memberRepository).flush();

    assertThatThrownBy(() -> memberService.updateNickname(1L, "새닉네임")).isSameAs(failure);
  }

  @Test
  @DisplayName("회원 탈퇴 시 Refresh Token을 삭제하고 회원 상태를 변경한다")
  void withdrawDeletesRefreshTokensAndWithdrawsMember() {
    Member member = createActiveMember("탈퇴회원");
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));

    memberService.withdraw(1L);

    verify(refreshTokenRepository).deleteAllByMember_Id(1L);
    assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    assertThat(member.getWithdrawnAt()).isNotNull();
  }

  private Member createActiveMember(String nickname) {
    return Member.createGoogleMember("google-id", "member@example.com", nickname, "profile.png");
  }
}
