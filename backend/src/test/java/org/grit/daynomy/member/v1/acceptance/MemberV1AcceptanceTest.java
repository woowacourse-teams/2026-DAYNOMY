package org.grit.daynomy.member.v1.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class MemberV1AcceptanceTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @LocalServerPort private int port;
  @Autowired private MemberRepository members;
  @Autowired private TokenService tokens;
  @Autowired private RefreshTokenRepository refreshTokens;

  @ParameterizedTest
  @ValueSource(strings = {"/api/users/me", "/api/v1/users/me"})
  void realAuthenticationCsrfUpdateAndWithdrawalHaveTheSameContract(String path) {
    Member owner = member();
    Member other = member();
    TokenPair pair = tokens.issue(owner.getId());
    TokenPair otherPair = tokens.issue(other.getId());
    Response csrf = given().port(port).get("/api/auth/csrf");
    assertThat(csrf.statusCode()).isEqualTo(200);

    given().port(port).get(path).then().statusCode(401);
    client(pair).get(path).then().statusCode(200).body("id", equalTo(owner.getId().intValue()));
    client(pair).body(Map.of("nickname", "without-csrf")).patch(path).then().statusCode(403);
    client(pair).delete(path).then().statusCode(403);

    String nickname = "v1-" + UUID.randomUUID().toString().substring(0, 10);
    withCsrf(pair, csrf)
        .body(Map.of("nickname", nickname, "id", other.getId()))
        .patch(path)
        .then()
        .statusCode(200)
        .body("id", equalTo(owner.getId().intValue()))
        .body("nickname", equalTo(nickname));
    client(pair).get(path).then().statusCode(200).body("nickname", equalTo(nickname));
    assertThat(members.findById(other.getId()).orElseThrow().getNickname())
        .isEqualTo(other.getNickname());

    withCsrf(pair, csrf)
        .body(Map.of("nickname", other.getNickname()))
        .patch(path)
        .then()
        .statusCode(409)
        .body("code", equalTo("NICKNAME_ALREADY_EXISTS"));
    assertThat(
            refreshTokens.findAll().stream()
                .filter(t -> t.getMember().getId().equals(owner.getId())))
        .hasSize(1);
    Response withdrawn = withCsrf(pair, csrf).delete(path);
    assertThat(withdrawn.statusCode()).isEqualTo(204);
    assertThat(withdrawn.headers().getValues("Set-Cookie"))
        .anySatisfy(value -> assertThat(value).contains("access_token=", "Path=/;", "Max-Age=0"))
        .anySatisfy(
            value -> assertThat(value).contains("refresh_token=", "Path=/api/auth", "Max-Age=0"));
    Member stored = members.findById(owner.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    assertThat(stored.getWithdrawnAt()).isNotNull();
    assertThat(
            refreshTokens.findAll().stream()
                .filter(t -> t.getMember().getId().equals(owner.getId())))
        .isEmpty();
    // A cryptographically valid access token stops working as soon as the member withdraws.
    client(pair).get(path).then().statusCode(401);
    withCsrf(pair, csrf).delete(path).then().statusCode(401);
    client(otherPair).get(path).then().statusCode(200);
  }

  private Member member() {
    String suffix = UUID.randomUUID().toString().substring(0, 10);
    return members.save(
        Member.createGoogleMember("lt746-" + suffix, suffix + "@example.invalid", suffix, null));
  }

  private RequestSpecification client(TokenPair pair) {
    return given()
        .port(port)
        .contentType("application/json")
        .cookie("access_token", pair.accessToken());
  }

  private RequestSpecification withCsrf(TokenPair pair, Response csrf) {
    return client(pair)
        .cookie("XSRF-TOKEN", csrf.cookie("XSRF-TOKEN"))
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
