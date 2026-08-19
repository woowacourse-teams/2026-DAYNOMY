package org.grit.daynomy.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.domain.RefreshToken;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
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

  @Transactional
  public TokenPair issue(Long memberId) {
    Member member = memberRepository.findById(memberId).orElseThrow(InvalidTokenException::new);

    if (member.isWithdrawn()) {
      throw new InvalidTokenException();
    }

    return issue(member);
  }

  @Transactional
  public TokenPair reissue(String rawRefreshToken) {
    Jwt jwt = jwtTokenProvider.parseRefreshToken(rawRefreshToken);
    String tokenHash = hash(rawRefreshToken);

    RefreshToken savedToken =
        refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow(InvalidTokenException::new);

    Long jwtMemberId = jwtTokenProvider.getMemberId(jwt);

    if (!savedToken.getMember().getId().equals(jwtMemberId)
        || savedToken.isExpired()
        || savedToken.getMember().isWithdrawn()) {
      throw new InvalidTokenException();
    }

    Member member = savedToken.getMember();
    refreshTokenRepository.delete(savedToken);

    return issue(member);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }

    refreshTokenRepository.deleteByTokenHash(hash(rawRefreshToken));
  }

  private TokenPair issue(Member member) {
    TokenPair tokenPair = jwtTokenProvider.createTokenPair(member.getId(), member.getRole());

    refreshTokenRepository.save(
        RefreshToken.create(
            member, hash(tokenPair.refreshToken()), tokenPair.refreshTokenExpiresAt()));

    return tokenPair;
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
