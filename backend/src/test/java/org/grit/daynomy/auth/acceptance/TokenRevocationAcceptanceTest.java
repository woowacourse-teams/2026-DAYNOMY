package org.grit.daynomy.auth.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
