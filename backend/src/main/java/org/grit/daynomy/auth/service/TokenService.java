package org.grit.daynomy.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.domain.RefreshToken;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.repository.RevokedAuthSessionRepository;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class TokenService {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MemberRepository memberRepository;
  private final RevokedAuthSessionRepository revokedSessions;

  public AuthenticatedMember authenticate(String rawAccessToken) {
    Jwt jwt = jwtTokenProvider.parseAccessToken(rawAccessToken);
    requireActiveSession(jwtTokenProvider.getSessionId(jwt));
    Member member =
        memberRepository
            .findByIdAndStatus(jwtTokenProvider.getMemberId(jwt), MemberStatus.ACTIVE)
            .orElseThrow(InvalidTokenException::new);
    return new AuthenticatedMember(member.getId(), member.getRole());
  }

  @Transactional
  public TokenPair issue(Long memberId) {
    Member member = memberRepository.findById(memberId).orElseThrow(InvalidTokenException::new);

    if (member.isWithdrawn()) {
      throw new InvalidTokenException();
    }

    return issue(member, UUID.randomUUID());
  }

  @Transactional
  public TokenPair reissue(String rawRefreshToken) {
    Jwt jwt = jwtTokenProvider.parseRefreshToken(rawRefreshToken);
    RefreshToken savedToken = validatedRefreshToken(rawRefreshToken, jwt);
    UUID sessionId = jwtTokenProvider.getSessionId(jwt);
    requireActiveSession(sessionId);
    Member member = savedToken.getMember();
    refreshTokenRepository.delete(savedToken);

    return issue(member, sessionId);
  }

  @Transactional
  public void logout(String rawAccessToken, String rawRefreshToken) {
    revokeSession(rawAccessToken, false);
    revokeSession(rawRefreshToken, true);
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      refreshTokenRepository.deleteByTokenHash(hash(rawRefreshToken));
    }
  }

  private TokenPair issue(Member member, UUID sessionId) {
    TokenPair tokenPair =
        jwtTokenProvider.createTokenPair(member.getId(), member.getRole(), sessionId);

    refreshTokenRepository.save(
        RefreshToken.create(
            member, hash(tokenPair.refreshToken()), tokenPair.refreshTokenExpiresAt()));

    return tokenPair;
  }

  private RefreshToken validatedRefreshToken(String rawRefreshToken, Jwt jwt) {
    RefreshToken saved =
        refreshTokenRepository
            .findByTokenHash(hash(rawRefreshToken))
            .orElseThrow(InvalidTokenException::new);
    if (!saved.getMember().getId().equals(jwtTokenProvider.getMemberId(jwt))
        || saved.isExpired()
        || saved.getMember().isWithdrawn()) {
      throw new InvalidTokenException();
    }
    return saved;
  }

  private void requireActiveSession(UUID sessionId) {
    if (revokedSessions.existsById(sessionId)) {
      throw new InvalidTokenException();
    }
  }

  private void revokeSession(String rawToken, boolean refresh) {
    if (rawToken == null || rawToken.isBlank()) return;
    try {
      Jwt jwt =
          refresh
              ? jwtTokenProvider.parseRefreshToken(rawToken)
              : jwtTokenProvider.parseAccessToken(rawToken);
      if (refresh) validatedRefreshToken(rawToken, jwt);
      revokedSessions.revoke(jwtTokenProvider.getSessionId(jwt));
    } catch (InvalidTokenException exception) {
      // 만료·변조된 쿠키가 섞여 있어도 다른 유효한 토큰의 세션 폐기와 쿠키 삭제를 진행한다.
    }
  }

  private String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
