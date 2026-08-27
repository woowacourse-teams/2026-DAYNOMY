package org.grit.daynomy.bookmark.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.http.Cookies;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.bookmark.repository.BookmarkRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
class BookmarkAcceptanceTest {

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("daynomy")
          .withUsername("daynomy")
          .withPassword("daynomy");

  @DynamicPropertySource
  static void configurePostgresql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @LocalServerPort private int port;

  @Autowired private MemberRepository memberRepository;

  @Autowired private AssetRepository assetRepository;

  @Autowired private BookmarkRepository bookmarkRepository;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    bookmarkRepository.deleteAll();
    assetRepository.deleteAll();
    memberRepository.deleteAll();
  }

  @Test
  @DisplayName("관심종목 추가, 조회, 삭제 흐름을 실제 HTTP 요청으로 검증한다")
  void manageBookmarks() {
    Member member =
        memberRepository.save(
            Member.createGoogleMember("google-1", "member@example.com", "회원", "profile.png"));
    Asset asset = assetRepository.save(new Asset("에코프로비엠", AssetCategory.STOCK, "247540"));
    String accessToken =
        jwtTokenProvider.createTokenPair(member.getId(), MemberRole.USER).accessToken();
    Csrf csrf = getCsrf();

    given()
        .port(port)
        .cookies(csrf.cookies())
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .header(csrf.headerName(), csrf.token())
        .contentType("application/json")
        .body(Map.of("targetId", asset.getId()))
        .when()
        .post("/api/assets/bookmarks")
        .then()
        .statusCode(201)
        .body("targetId", equalTo(asset.getId().intValue()))
        .body("assetName", equalTo("에코프로비엠"));

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .when()
        .get("/api/users/me/bookmarks")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].targetId", equalTo(asset.getId().intValue()))
        .body("[0].assetName", equalTo("에코프로비엠"));

    given()
        .port(port)
        .cookies(csrf.cookies())
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .header(csrf.headerName(), csrf.token())
        .queryParam("targetId", asset.getId())
        .when()
        .delete("/api/assets/bookmarks")
        .then()
        .statusCode(204);

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .when()
        .get("/api/users/me/bookmarks")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  private Csrf getCsrf() {
    var response = given().port(port).when().get("/api/auth/csrf").then().statusCode(200).extract();
    return new Csrf(
        response.path("headerName"), response.path("token"), response.response().detailedCookies());
  }

  private record Csrf(String headerName, String token, Cookies cookies) {}
}
