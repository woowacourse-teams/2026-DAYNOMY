package org.grit.daynomy.news.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class AdminNewsSearchAcceptanceTest {

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
  @Autowired private NewsRepository newsRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    newsRepository.deleteAll();
    memberRepository.deleteAll();
  }

  @Test
  @DisplayName("관리자는 여러 상태의 뉴스를 다중 단어·관련도·필터·페이지 조건으로 검색한다")
  void searchAdminNews() {
    String accessToken = createAccessToken(MemberRole.ADMIN);
    News title =
        newsRepository.save(News.createDraft("기준금리 추가 인하", "본문", null, List.of(), Category.ETF));
    newsRepository.save(
        News.createPublished(
            "시장 전망",
            "금리 추가 인하",
            null,
            List.of(),
            Category.ETF,
            Instant.parse("2026-08-20T10:00:00Z")));
    News rejected = News.createDraft("인하 전망", "금리", null, List.of(), Category.ETF);
    rejected.reject();
    newsRepository.save(rejected);
    newsRepository.save(News.createDraft("금리 동결", "본문", null, List.of(), Category.ETF));
    newsRepository.save(News.createDraft("금리 인하", "본문", null, List.of(), Category.STOCK));

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .queryParam("q", "  금리\t인하 금리  ")
        .queryParam("category", "ETF")
        .queryParam("sort", "RELEVANCE")
        .queryParam("size", 1)
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(title.getId().intValue()))
        .body("items[0].status", equalTo("DRAFT"))
        .body("page", equalTo(1))
        .body("totalElements", equalTo(3))
        .body("totalPages", equalTo(3))
        .body("hasNext", equalTo(true));

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .queryParam("q", "금리 인하")
        .queryParam("category", "ETF")
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(200)
        .body("items", hasSize(3))
        .body("items[0].id", equalTo(rejected.getId().intValue()));

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .queryParam("q", "인하 금리")
        .queryParam("status", "DRAFT")
        .queryParam("category", "ETF")
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(title.getId().intValue()))
        .body("totalElements", equalTo(1));

    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .queryParam("q", "\u00a0\t")
        .queryParam("category", "ETF")
        .queryParam("sort", "RELEVANCE")
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(200)
        .body("items", hasSize(4))
        .body("items[0].title", equalTo("금리 동결"));
  }

  @Test
  @DisplayName("관리자 검색은 인증하지 않은 요청과 일반 회원을 거부한다")
  void searchAdminNewsRequiresAdmin() {
    given()
        .port(port)
        .queryParam("q", "금리 인하")
        .queryParam("sort", "RELEVANCE")
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(401);

    String accessToken = createAccessToken(MemberRole.USER);
    given()
        .port(port)
        .cookie(TokenCookieManager.ACCESS_TOKEN_COOKIE, accessToken)
        .queryParam("q", "금리 인하")
        .queryParam("sort", "RELEVANCE")
        .when()
        .get("/api/admin/news")
        .then()
        .statusCode(403);
  }

  private String createAccessToken(MemberRole role) {
    Member member =
        memberRepository.save(
            Member.createGoogleMember(
                "admin-search-test", "admin-search@example.com", "검색테스트", null));
    jdbcTemplate.update("UPDATE members SET role = ? WHERE id = ?", role.name(), member.getId());
    return jwtTokenProvider.createTokenPair(member.getId(), role).accessToken();
  }
}
