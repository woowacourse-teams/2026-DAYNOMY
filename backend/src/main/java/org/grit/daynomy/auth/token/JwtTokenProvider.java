package org.grit.daynomy.auth.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.grit.daynomy.member.domain.MemberRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String TOKEN_TYPE = "token_type";
  private static final String ACCESS_TOKEN = "access";
  private static final String REFRESH_TOKEN = "refresh";

  private final String issuer;
  private final Duration accessTokenExpiration;
  private final Duration refreshTokenExpiration;
  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;

  public JwtTokenProvider(
      @Value("${jwt.issuer}") String issuer,
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {

    byte[] keyBytes = Base64.getDecoder().decode(secret);

    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT 비밀키는 32바이트 이상이어야 합니다.");
    }

    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

    this.issuer = issuer;
    this.accessTokenExpiration = Duration.ofMillis(accessTokenExpiration);
    this.refreshTokenExpiration = Duration.ofMillis(refreshTokenExpiration);
    this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();

    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    this.jwtDecoder = decoder;
  }

  public TokenPair createTokenPair(Long memberId, MemberRole role) {
    Instant now = Instant.now();
    Instant accessExpiresAt = now.plus(accessTokenExpiration);
    Instant refreshExpiresAt = now.plus(refreshTokenExpiration);

    String accessToken = encode(memberId, role, ACCESS_TOKEN, now, accessExpiresAt);
    String refreshToken = encode(memberId, null, REFRESH_TOKEN, now, refreshExpiresAt);

    return new TokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
  }

  public Jwt parseAccessToken(String token) {
    return parse(token, ACCESS_TOKEN);
  }

  public Jwt parseRefreshToken(String token) {
    return parse(token, REFRESH_TOKEN);
  }

  public Long getMemberId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException exception) {
      throw new InvalidTokenException();
    }
  }

  public MemberRole getRole(Jwt jwt) {
    try {
      return MemberRole.valueOf(jwt.getClaimAsString("role"));
    } catch (RuntimeException exception) {
      throw new InvalidTokenException();
    }
  }

  private String encode(
      Long memberId, MemberRole role, String tokenType, Instant issuedAt, Instant expiresAt) {

    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(memberId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim(TOKEN_TYPE, tokenType);

    if (role != null) {
      claims.claim("role", role.name());
    }

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  private Jwt parse(String token, String expectedTokenType) {
    try {
      Jwt jwt = jwtDecoder.decode(token);

      if (!expectedTokenType.equals(jwt.getClaimAsString(TOKEN_TYPE))) {
        throw new InvalidTokenException();
      }

      return jwt;
    } catch (JwtException | IllegalArgumentException exception) {
      throw new InvalidTokenException();
    }
  }
}
