package org.grit.daynomy.auth.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.domain.RefreshToken;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.repository.RevokedAuthSessionRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRevocationAcceptanceTest {
  @Container
  static final PostgreSQLContainer DB =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DB::getJdbcUrl);
    properties.add("spring.datasource.username", DB::getUsername);
    properties.add("spring.datasource.password", DB::getPassword);
    properties.add("spring.datasource.driver-class-name", DB::getDriverClassName);
  }

  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired AssetRepository assets;
  @Autowired JwtTokenProvider jwt;
  @Autowired TokenService tokens;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired RevokedAuthSessionRepository revoked;
  @Autowired JdbcTemplate jdbc;

  @Value("${jwt.secret}")
  String signingKey;

  @Value("${jwt.issuer}")
  String issuer;

  @Test
  void changingDatabaseRoleImmediatelyChangesExistingTokensAuthority() {
    Member administrator = member();
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", administrator.getId());
    TokenPair session = tokens.issue(administrator.getId());
    String marker = "다른 회원의 비공개 피드백 " + UUID.randomUUID();
    client(null)
        .body(Map.of("content", marker, "pagePath", "/"))
        .post("/api/feedback")
        .then()
        .statusCode(204);
    var before = client(session.accessToken()).get("/api/admin/feedbacks?size=100");
    before.then().statusCode(200);
    assertThat(before.asString()).contains(marker);

    jdbc.update("UPDATE members SET role='USER' WHERE id=?", administrator.getId());

    client(session.accessToken()).get("/api/admin/feedbacks").then().statusCode(403);
    client(session.accessToken()).post("/api/admin/league/recalculate").then().statusCode(403);
    client(session.accessToken())
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("role", equalTo("USER"));
    var refreshed = refresh(session);
    client(refreshed.accessToken()).get("/api/admin/feedbacks").then().statusCode(403);
    assertThat(jwt.getSessionId(jwt.parseAccessToken(refreshed.accessToken())))
        .isEqualTo(jwt.getSessionId(jwt.parseAccessToken(session.accessToken())));
  }

  @Test
  void logoutRevokesAccessBeforeAndAfterRotationButKeepsOtherDevicesAndOtherMembers() {
    Member owner = member();
    Member other = member();
    TokenPair initial = tokens.issue(owner.getId());
    TokenPair anotherDevice = tokens.issue(owner.getId());
    TokenPair anotherMember = tokens.issue(other.getId());
    String code = UUID.randomUUID().toString();
    Asset asset =
        assets.save(
            Asset.listedStock(
                "세션 보호 종목",
                code,
                StockMarket.KOSPI,
                "KR" + code.replace("-", "").substring(0, 10),
                java.time.LocalDate.now()));
    client(initial.accessToken())
        .body(
            Map.of(
                "assetId", asset.getId(),
                "type", "BUY",
                "tradeDate", java.time.LocalDate.now().toString(),
                "quantity", 7,
                "unitPrice", 100,
                "fee", 0,
                "tax", 0))
        .post("/api/users/me/portfolio/transactions")
        .then()
        .statusCode(201);
    TokenPair rotated = refresh(initial);
    UUID sessionId = jwt.getSessionId(jwt.parseAccessToken(initial.accessToken()));
    assertThat(jwt.getSessionId(jwt.parseAccessToken(rotated.accessToken()))).isEqualTo(sessionId);
    assertThat(jwt.getSessionId(jwt.parseRefreshToken(rotated.refreshToken())))
        .isEqualTo(sessionId);
    client(initial.accessToken()).get("/api/users/me/portfolio").then().statusCode(200);
    client(rotated.accessToken()).get("/api/users/me/portfolio").then().statusCode(200);

    for (int attempt = 0; attempt < 2; attempt++) {
      client(rotated.accessToken())
          .cookie("refresh_token", rotated.refreshToken())
          .post("/api/auth/logout")
          .then()
          .statusCode(204);
    }

    assertThat(revoked.existsById(sessionId)).isTrue();
    for (TokenPair expiredSession : List.of(initial, rotated)) {
      client(expiredSession.accessToken())
          .get("/api/users/me/portfolio")
          .then()
          .statusCode(401)
          .body("code", equalTo("INVALID_TOKEN"));
      client(expiredSession.accessToken())
          .body(Map.of("quantity", 9, "averagePurchasePrice", 1))
          .patch("/api/users/me/portfolio/holdings/" + asset.getId())
          .then()
          .statusCode(401);
      client(null)
          .cookie("refresh_token", expiredSession.refreshToken())
          .post("/api/auth/refresh")
          .then()
          .statusCode(401);
    }
    client(anotherDevice.accessToken())
        .get("/api/users/me/portfolio")
        .then()
        .statusCode(200)
        .body("holdings[0].quantity", equalTo(7));
    client(refresh(anotherDevice).accessToken()).get("/api/users/me").then().statusCode(200);
    client(anotherMember.accessToken())
        .get("/api/users/me/portfolio")
        .then()
        .statusCode(200)
        .body("holdings", hasSize(0));
    client(tokens.issue(owner.getId()).accessToken()).get("/api/users/me").then().statusCode(200);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "invalid-access-cookie")
  void refreshCookieAloneCanRevokeTheSessionEvenWithAnInvalidAccessCookie(String access) {
    TokenPair session = tokens.issue(member().getId());
    client(access)
        .cookie("refresh_token", session.refreshToken())
        .post("/api/auth/logout")
        .then()
        .statusCode(204);
    client(session.accessToken()).get("/api/users/me").then().statusCode(401);
    client(null)
        .cookie("refresh_token", session.refreshToken())
        .post("/api/auth/refresh")
        .then()
        .statusCode(401);
  }

  @Test
  void accessCookieAloneRevokesTheSessionAndStillStoredRefreshTokenCannotRestoreIt() {
    TokenPair session = tokens.issue(member().getId());
    client(session.accessToken()).post("/api/auth/logout").then().statusCode(204);
    client(session.accessToken()).get("/api/users/me").then().statusCode(401);
    client(null)
        .cookie("refresh_token", session.refreshToken())
        .post("/api/auth/refresh")
        .then()
        .statusCode(401)
        .body("code", equalTo("INVALID_REFRESH_TOKEN"));
    assertThat(revoked.existsById(jwt.getSessionId(jwt.parseAccessToken(session.accessToken()))))
        .isTrue();
  }

  @Test
  void sessionlessLegacyAccessIsRejectedButLegacyRefreshMigratesWithoutAnotherLogin()
      throws Exception {
    Member owner = member();
    Instant expiresAt = Instant.now().plusSeconds(86400);
    String oldAccess = legacyToken(owner, "access", Instant.now().plusSeconds(1800));
    String oldRefresh = legacyToken(owner, "refresh", expiresAt);
    refreshTokens.saveAndFlush(
        RefreshToken.create(
            owner,
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(oldRefresh.getBytes(StandardCharsets.UTF_8))),
            expiresAt));
    client(oldAccess).get("/api/users/me").then().statusCode(401);

    Response response =
        client(oldAccess)
            .cookie("refresh_token", oldRefresh)
            .post("/api/auth/refresh")
            .then()
            .statusCode(204)
            .extract()
            .response();
    String migratedAccess = response.cookie("access_token");
    String migratedRefresh = response.cookie("refresh_token");
    client(migratedAccess).get("/api/users/me").then().statusCode(200);
    assertThat(jwt.getSessionId(jwt.parseAccessToken(migratedAccess)))
        .isEqualTo(UUID.fromString(jwt.parseRefreshToken(oldRefresh).getId()));
    client(migratedAccess)
        .cookie("refresh_token", migratedRefresh)
        .post("/api/auth/logout")
        .then()
        .statusCode(204);
    client(migratedAccess).get("/api/users/me").then().statusCode(401);
    client(null)
        .cookie("refresh_token", migratedRefresh)
        .post("/api/auth/refresh")
        .then()
        .statusCode(401);
  }

  private TokenPair refresh(TokenPair session) {
    var response =
        client(session.accessToken())
            .cookie("refresh_token", session.refreshToken())
            .post("/api/auth/refresh")
            .then()
            .statusCode(204)
            .extract()
            .response();
    String access = response.cookie("access_token");
    String refresh = response.cookie("refresh_token");
    return new TokenPair(
        access,
        refresh,
        jwt.parseAccessToken(access).getExpiresAt(),
        jwt.parseRefreshToken(refresh).getExpiresAt());
  }

  private String legacyToken(Member member, String type, Instant expiresAt) {
    var encoder =
        NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(Base64.getDecoder().decode(signingKey), "HmacSHA256"))
            .build();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(member.getId().toString())
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("token_type", type)
            .claim("role", "USER")
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
        .getTokenValue();
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private RequestSpecification client(String access) {
    var csrf = given().port(port).get("/api/auth/csrf");
    var request =
        given()
            .port(port)
            .contentType("application/json")
            .cookies(csrf.cookies())
            .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
    return access == null ? request : request.cookie("access_token", access);
  }
}
