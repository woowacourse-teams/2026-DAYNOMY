package org.grit.daynomy.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.grit.daynomy.auth.domain.RefreshToken;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  @Mock private JwtTokenProvider jwtTokenProvider;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  @Mock private MemberRepository memberRepository;

  @InjectMocks private TokenService tokenService;

  @Test
  @DisplayName("활성 회원에게 토큰을 발급하고 Refresh Token의 해시를 저장한다")
  void issueCreatesTokenPairAndStoresRefreshTokenHash() {
    Member member = createActiveMember(1L);
    TokenPair tokenPair = createTokenPair();
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));
    given(jwtTokenProvider.createTokenPair(1L, MemberRole.USER)).willReturn(tokenPair);

    TokenPair result = tokenService.issue(1L);

    assertThat(result).isSameAs(tokenPair);
    verify(refreshTokenRepository)
        .save(
            argThat(
                refreshToken ->
                    refreshToken.getMember() == member
                        && refreshToken.getTokenHash().equals(sha256("issued-refresh-token"))
                        && refreshToken.getExpiresAt().equals(tokenPair.refreshTokenExpiresAt())));
  }

  @Test
  @DisplayName("토큰을 발급할 회원이 없으면 예외를 던진다")
  void issueThrowsWhenMemberIsMissing() {
    given(memberRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> tokenService.issue(1L)).isInstanceOf(InvalidTokenException.class);
    verifyNoInteractions(jwtTokenProvider, refreshTokenRepository);
  }

  @Test
  @DisplayName("탈퇴한 회원에게는 토큰을 발급하지 않는다")
  void issueThrowsWhenMemberIsWithdrawn() {
    Member member = mock(Member.class);
    given(member.isWithdrawn()).willReturn(true);
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> tokenService.issue(1L)).isInstanceOf(InvalidTokenException.class);
    verifyNoInteractions(jwtTokenProvider, refreshTokenRepository);
  }

  @Test
  @DisplayName("유효한 Refresh Token을 회전하여 새로운 토큰을 발급한다")
  void reissueRotatesRefreshToken() {
    String rawRefreshToken = "saved-refresh-token";
    Member member = createActiveMember(1L);
    Jwt jwt = mock(Jwt.class);
    RefreshToken savedToken =
        RefreshToken.create(member, sha256(rawRefreshToken), Instant.parse("2999-08-24T00:00:00Z"));
    TokenPair newTokenPair = createTokenPair();
    given(jwtTokenProvider.parseRefreshToken(rawRefreshToken)).willReturn(jwt);
    given(refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)))
        .willReturn(Optional.of(savedToken));
    given(jwtTokenProvider.getMemberId(jwt)).willReturn(1L);
    given(jwtTokenProvider.createTokenPair(1L, MemberRole.USER)).willReturn(newTokenPair);

    TokenPair result = tokenService.reissue(rawRefreshToken);

    assertThat(result).isSameAs(newTokenPair);
    verify(refreshTokenRepository).delete(savedToken);
    verify(refreshTokenRepository)
        .save(
            argThat(
                refreshToken ->
                    refreshToken.getMember() == member
                        && refreshToken.getTokenHash().equals(sha256("issued-refresh-token"))));
  }

  @Test
  @DisplayName("JWT 회원과 저장된 Refresh Token의 회원이 다르면 재발급하지 않는다")
  void reissueThrowsWhenMemberDoesNotMatch() {
    String rawRefreshToken = "saved-refresh-token";
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    Jwt jwt = mock(Jwt.class);
    RefreshToken savedToken =
        RefreshToken.create(member, sha256(rawRefreshToken), Instant.parse("2999-08-24T00:00:00Z"));
    given(jwtTokenProvider.parseRefreshToken(rawRefreshToken)).willReturn(jwt);
    given(refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)))
        .willReturn(Optional.of(savedToken));
    given(jwtTokenProvider.getMemberId(jwt)).willReturn(2L);

    assertThatThrownBy(() -> tokenService.reissue(rawRefreshToken))
        .isInstanceOf(InvalidTokenException.class);
    verify(refreshTokenRepository, never()).delete(savedToken);
  }

  @Test
  @DisplayName("만료된 Refresh Token이면 재발급하지 않는다")
  void reissueThrowsWhenRefreshTokenIsExpired() {
    String rawRefreshToken = "expired-refresh-token";
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    Jwt jwt = mock(Jwt.class);
    RefreshToken savedToken =
        RefreshToken.create(member, sha256(rawRefreshToken), Instant.parse("2000-01-01T00:00:00Z"));
    given(jwtTokenProvider.parseRefreshToken(rawRefreshToken)).willReturn(jwt);
    given(refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)))
        .willReturn(Optional.of(savedToken));
    given(jwtTokenProvider.getMemberId(jwt)).willReturn(1L);

    assertThatThrownBy(() -> tokenService.reissue(rawRefreshToken))
        .isInstanceOf(InvalidTokenException.class);
    verify(refreshTokenRepository, never()).delete(savedToken);
  }

  @Test
  @DisplayName("Refresh Token이 없으면 로그아웃 시 저장소에 접근하지 않는다")
  void logoutDoesNothingWhenRefreshTokenIsMissing() {
    tokenService.logout(null);
    tokenService.logout(" ");

    verifyNoInteractions(refreshTokenRepository);
  }

  @Test
  @DisplayName("로그아웃하면 Refresh Token의 해시로 저장된 토큰을 삭제한다")
  void logoutDeletesRefreshTokenByHash() {
    tokenService.logout("saved-refresh-token");

    verify(refreshTokenRepository).deleteByTokenHash(sha256("saved-refresh-token"));
  }

  private Member createActiveMember(Long memberId) {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(memberId);
    given(member.getRole()).willReturn(MemberRole.USER);
    given(member.isWithdrawn()).willReturn(false);
    return member;
  }

  private TokenPair createTokenPair() {
    return new TokenPair(
        "issued-access-token",
        "issued-refresh-token",
        Instant.parse("2026-08-24T01:00:00Z"),
        Instant.parse("2026-09-07T00:00:00Z"));
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
