package org.grit.daynomy.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private RefreshTokenRepository refreshTokenRepository;

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
    verify(memberRepository, never()).save(any(Member.class));
  }

  @Test
  @DisplayName("기존 Google 회원이 없으면 새로운 회원을 저장한다")
  void findOrCreateGoogleMemberCreatesMemberWhenMissing() {
    given(memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-id"))
        .willReturn(Optional.empty());
    given(memberRepository.save(any(Member.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    Member member =
        memberService.findOrCreateGoogleMember(
            "google-id", "member@example.com", "daynomy", "profile.png");

    assertThat(member.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
    assertThat(member.getProviderId()).isEqualTo("google-id");
    assertThat(member.getEmail()).isEqualTo("member@example.com");
    assertThat(member.getNickname()).isEqualTo("daynomy");
    assertThat(member.getProfileImageUrl()).isEqualTo("profile.png");
    assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
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
  @DisplayName("닉네임의 앞뒤 공백을 제거하고 수정한다")
  void updateNicknameNormalizesAndUpdatesNickname() {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));
    given(memberRepository.existsByNickname("새닉네임")).willReturn(false);

    Member result = memberService.updateNickname(1L, "  새닉네임  ");

    assertThat(result).isSameAs(member);
    assertThat(member.getNickname()).isEqualTo("새닉네임");
    verify(memberRepository).existsByNickname("새닉네임");
  }

  @Test
  @DisplayName("다른 회원이 사용 중인 닉네임이면 수정하지 않고 예외를 던진다")
  void updateNicknameThrowsWhenNicknameAlreadyExists() {
    Member member = createActiveMember("기존닉네임");
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));
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
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));

    memberService.updateNickname(1L, "  기존닉네임  ");

    assertThat(member.getNickname()).isEqualTo("기존닉네임");
    verify(memberRepository, never()).existsByNickname(any(String.class));
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
