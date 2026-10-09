package org.grit.daynomy.league.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.member.service.MemberService;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class SharedPortfolioAcceptanceTest {
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
  @Autowired private MemberService memberService;
  @Autowired private AssetRepository assets;
  @Autowired private StockDailyPriceRepository prices;
  @Autowired private PortfolioRepository originals;
  @Autowired private PortfolioHoldingRepository originalHoldings;
  @Autowired private PortfolioHoldingHistoryRepository originalHistory;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private TokenService tokenService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LeagueReturnService leagueReturns;
  private static final String SHARED = "/api/users/me/shared-portfolio";

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private Asset asset() {
    String code = UUID.randomUUID().toString();
    return assets.save(
        Asset.listedStock(
            "테스트 종목",
            code,
            StockMarket.KOSPI,
            "KR" + code.replace("-", "").substring(0, 10),
            LocalDate.now()));
  }

  private RequestSpecification client(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }

  @Test
  void importAndVisibilityNeverChangePositionOrWriteOriginalOrAnotherAccount() {
    Member owner = member();
    Asset asset = asset();
    Portfolio original = originals.save(Portfolio.create(owner));
    originalHoldings.save(new PortfolioHolding(original, asset, 10, new BigDecimal("70000")));
    prices.save(new StockDailyPrice(asset, LocalDate.now(), new BigDecimal("77000")));
    String request =
        """
        {"holdings":[{"assetId":%d,"quantity":10,"averagePurchasePrice":70000}],"overwriteExisting":false}
        """
            .formatted(asset.getId());
    client(owner)
        .body(request)
        .post(SHARED + "/import")
        .then()
        .statusCode(200)
        .body("holdings[0].quantity", equalTo(10))
        .body("totalReturnRate", equalTo(10.0f));
    client(owner)
        .body(request)
        .post(SHARED + "/import")
        .then()
        .statusCode(409)
        .body("code", equalTo("SHARED_IMPORT_CONFLICT"));
    jdbc.update(
        "UPDATE shared_holdings SET reason=? WHERE portfolio_id=(SELECT id FROM shared_portfolios WHERE member_id=?)",
        "기존 판단 근거",
        owner.getId());
    String visibility = SHARED + "/holdings/" + asset.getId() + "/visibility";
    // 숨김 API에 자산 값을 함께 보내더라도 수량·매수가·기존 근거는 변경하지 않는다.
    client(owner)
        .body(
            "{\"hidden\":true,\"quantity\":2,\"averagePurchasePrice\":75000,\"reason\":\"덮어쓰기 시도\"}")
        .patch(visibility)
        .then()
        .statusCode(200)
        .header("Cache-Control", "no-store")
        .body("holdings[0].hidden", equalTo(true))
        .body("holdings[0].quantity", equalTo(10))
        .body("holdings[0].averagePurchasePrice", equalTo(70000.0f))
        .body("holdings[0].reason", equalTo("기존 판단 근거"))
        .body("totalReturnRate", equalTo(10.0f));
    client(owner).body("{}").patch(visibility).then().statusCode(400);
    client(owner).body("{\"hidden\":null}").patch(visibility).then().statusCode(400);
    client(owner)
        .body("{\"hidden\":true}")
        .patch(SHARED + "/holdings/" + asset().getId() + "/visibility")
        .then()
        .statusCode(404);
    Member other = member();
    client(other).get(SHARED).then().statusCode(200).body("holdings", hasSize(0));
    client(other).body("{\"hidden\":false}").patch(visibility).then().statusCode(404);
    client(owner)
        .body("{\"quantity\":2,\"averagePurchasePrice\":75000,\"hidden\":false,\"reason\":\"\"}")
        .put(SHARED + "/holdings/" + asset.getId())
        .then()
        .statusCode(404);
    client(owner).delete(SHARED + "/holdings/" + asset.getId()).then().statusCode(404);
    client(owner)
        .body("{\"transactionType\":\"BUY\",\"assetId\":" + asset.getId() + "}")
        .post("/api/users/me/portfolio/transactions")
        .then()
        .statusCode(405);
    client(owner)
        .get(SHARED)
        .then()
        .statusCode(200)
        .body("holdings", hasSize(1))
        .body("holdings[0].quantity", equalTo(10))
        .body("holdings[0].hidden", equalTo(true));
    client(owner)
        .body(request.replace("false", "true"))
        .post(SHARED + "/import")
        .then()
        .statusCode(200)
        .body("holdings[0].quantity", equalTo(10))
        .body("holdings[0].hidden", equalTo(true))
        .body("holdings[0].reason", equalTo("기존 판단 근거"));
    client(owner)
        .body("{\"hidden\":false}")
        .patch(visibility)
        .then()
        .statusCode(200)
        .body("holdings[0].hidden", equalTo(false));
    var preserved =
        originalHoldings.findByPortfolioIdAndAssetId(original.getId(), asset.getId()).orElseThrow();
    assertThat(preserved.getQuantity()).isEqualTo(10);
    assertThat(preserved.getAveragePurchasePrice()).isEqualByComparingTo("70000");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared_holding_histories WHERE portfolio_id ="
                    + " (SELECT id FROM shared_portfolios WHERE member_id = ?)",
                Integer.class,
                owner.getId()))
        .isEqualTo(1);

    client(owner)
        .body(request.replace("false", "true").replace("70000", "70000.00"))
        .post(SHARED + "/import")
        .then()
        .statusCode(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared_holding_histories WHERE portfolio_id ="
                    + " (SELECT id FROM shared_portfolios WHERE member_id = ?)",
                Integer.class,
                owner.getId()))
        .isEqualTo(1);

    client(owner)
        .body(request.replace("false", "true").replace("\"quantity\":10", "\"quantity\":20"))
        .post(SHARED + "/import")
        .then()
        .statusCode(200)
        .body("holdings[0].quantity", equalTo(20));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared_holding_histories WHERE portfolio_id ="
                    + " (SELECT id FROM shared_portfolios WHERE member_id = ?)",
                Integer.class,
                owner.getId()))
        .isEqualTo(2);
    assertThat(
            originalHoldings
                .findByPortfolioIdAndAssetId(original.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(10);
  }

  @Test
  void originalHistoriesUseIdToOrderChangesAtTheSameInstant() {
    Member owner = member();
    Asset asset = asset();
    Portfolio original = originals.save(Portfolio.create(owner));
    Instant changedAt = Instant.now();
    for (long quantity : new long[] {1, 3, 5}) {
      jdbc.update(
          """
          INSERT INTO portfolio_holding_histories
            (portfolio_id,asset_id,change_type,quantity,average_purchase_price,created_at,updated_at)
          VALUES (?,?,?,?,10000,?,?)
          """,
          original.getId(),
          asset.getId(),
          quantity == 1 ? "ADDED" : "UPDATED",
          quantity,
          java.sql.Timestamp.from(changedAt),
          java.sql.Timestamp.from(changedAt));
    }
    assertThat(
            originalHistory.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                original.getId(), changedAt.plusSeconds(1)))
        .extracting(history -> history.getQuantity())
        .containsExactly(1L, 3L, 5L);
    LocalDate today = changedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
    client(owner)
        .queryParam("from", today.toString())
        .queryParam("to", today.toString())
        .get("/api/portfolio/histories")
        .then()
        .statusCode(200)
        .body("quantity", contains(5, 3, 1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"SHARED_IMPORT_CONFLICT", "DUPLICATE_SHARED_ASSET", "INVALID_LEAGUE_ASSET"})
  void failedBatchImportRollsBackEarlierHoldingsAndHistory(String errorCode) {
    Member owner = member();
    Asset existing = asset();
    Asset added = asset();
    String holding = "{\"assetId\":%d,\"quantity\":%d,\"averagePurchasePrice\":70000}";
    client(owner)
        .body(
            "{\"holdings\":[%s],\"overwriteExisting\":false}"
                .formatted(holding.formatted(existing.getId(), 10)))
        .post(SHARED + "/import")
        .then()
        .statusCode(200);
    boolean overwrite = !errorCode.equals("SHARED_IMPORT_CONFLICT");
    long failingAssetId = errorCode.equals("INVALID_LEAGUE_ASSET") ? Long.MAX_VALUE : added.getId();

    client(owner)
        .body(
            "{\"holdings\":[%s,%s,%s],\"overwriteExisting\":%s}"
                .formatted(
                    holding.formatted(added.getId(), 5),
                    holding.formatted(existing.getId(), 20),
                    holding.formatted(failingAssetId, 1),
                    overwrite))
        .post(SHARED + "/import")
        .then()
        .statusCode(overwrite ? 400 : 409)
        .body("code", equalTo(errorCode));

    client(owner)
        .get(SHARED)
        .then()
        .statusCode(200)
        .body("holdings", hasSize(1))
        .body("holdings[0].assetId", equalTo(existing.getId().intValue()))
        .body("holdings[0].quantity", equalTo(10));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared_holding_histories WHERE portfolio_id ="
                    + " (SELECT id FROM shared_portfolios WHERE member_id = ?)",
                Integer.class,
                owner.getId()))
        .isEqualTo(1);
  }

  @Test
  void removedFinanceApisAreUnavailableAndAbsentFromOpenApi() {
    Member owner = member();
    for (String path :
        new String[] {
          "/api/users/me/learning/progress",
          "/api/users/me/learning/check-ins",
          "/api/users/me/learning/mock-trades",
          "/api/users/me/financial-plans"
        }) {
      client(owner).get(path).then().statusCode(404);
    }
    Map<String, Object> paths =
        given()
            .port(port)
            .get("/v3/api-docs")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("paths");
    assertThat(paths.keySet())
        .noneMatch(
            path ->
                path.startsWith("/api/users/me/learning")
                    || path.startsWith("/api/users/me/financial-plans"));
  }

  @Test
  void decisionReviewPublicationRankingAndPublicDetailsUseRealStoredData() {
    Member owner = member();
    Member viewer = member();
    Asset asset = asset();
    Portfolio original = originals.save(Portfolio.create(owner));
    originalHoldings.save(new PortfolioHolding(original, asset, 10, new BigDecimal("70000")));
    client(owner)
        .body(
            """
        {"holdings":[{"assetId":%d,"quantity":10,"averagePurchasePrice":70000}],"overwriteExisting":false}
        """
                .formatted(asset.getId()))
        .post(SHARED + "/import")
        .then()
        .statusCode(200);
    String decision =
        """
        {"requestKey":"holding-decision-1","assetId":%d,"decision":{"reason":"실적을 확인한 보유 판단",
        "expectedHoldingPeriod":"OVER_SIX_MONTHS","expectedChange":"매출 성장","invalidationCondition":"실적 악화",
        "maximumAcceptableLossRate":10}}
        """
            .formatted(asset.getId());
    given()
        .port(port)
        .contentType("application/json")
        .cookie(
            "access_token", tokens.createTokenPair(owner.getId(), MemberRole.USER).accessToken())
        .body(decision)
        .post("/api/users/me/portfolio/decisions")
        .then()
        .statusCode(403);
    long id =
        client(owner)
            .body(decision)
            .post("/api/users/me/portfolio/decisions")
            .then()
            .statusCode(201)
            .body("transactionType", equalTo("HOLD"))
            .extract()
            .jsonPath()
            .getLong("id");
    client(owner)
        .body(decision)
        .post("/api/users/me/portfolio/decisions")
        .then()
        .statusCode(201)
        .body("id", equalTo((int) id));
    client(owner)
        .body(decision.replace("실적을 확인한 보유 판단", "다른 판단"))
        .post("/api/users/me/portfolio/decisions")
        .then()
        .statusCode(409);
    client(owner).get(SHARED).then().statusCode(200).body("holdings[0].quantity", equalTo(10));
    client(viewer).body(decision).post("/api/users/me/portfolio/decisions").then().statusCode(404);
    String review =
        """
        {"actualResult":"예상을 점검했습니다","differenceFromExpectation":"가격은 예상과 달랐습니다","nextAction":"다음 실적을 확인합니다"}
        """;
    client(viewer)
        .body(review)
        .post("/api/users/me/portfolio/decisions/" + id + "/reviews")
        .then()
        .statusCode(404);
    client(owner)
        .body(review)
        .post("/api/users/me/portfolio/decisions/" + id + "/reviews")
        .then()
        .statusCode(201);
    client(owner)
        .get("/api/users/me/portfolio/transactions")
        .then()
        .statusCode(200)
        .body("transactions", hasSize(1))
        .body("transactions[0].reviews", hasSize(1));
    String publicId =
        client(owner)
            .body(
                """
        {"displayName":"%s","experienceLevel":"BEGINNER","riskProfile":"BALANCED","bio":"기록하며 배웁니다"}
        """
                    .formatted(UUID.randomUUID().toString().substring(0, 12)))
            .put("/api/users/me/investor-profile")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("publicId");
    client(owner)
        .body(
            """
        {"profilePublic":true,"leagueEnabled":true,"allocationPublic":true,"detailPublic":true}
        """)
        .put("/api/users/me/portfolio/publication")
        .then()
        .statusCode(200);
    given()
        .port(port)
        .get("/api/league/investors/" + publicId)
        .then()
        .statusCode(200)
        .body("decisions", nullValue())
        .body("holdings", nullValue());
    String detail = "/api/league/investors/" + publicId + "/details";
    client(viewer).post("/api/users/me/league/follows/" + publicId).then().statusCode(204);
    client(viewer)
        .get("/api/users/me/league/follows/summary")
        .then()
        .statusCode(200)
        .body("investors.publicId", hasItem(publicId));
    client(owner)
        .get("/api/users/me/league/follows/summary")
        .then()
        .statusCode(200)
        .body("investors", hasSize(0));
    given()
        .port(port)
        .get(detail)
        .then()
        .statusCode(200)
        .header("Cache-Control", "no-store")
        .body("holdings", hasSize(1))
        .body("holdings[0].weight", nullValue())
        .body("holdings[0].weeklyContributionRate", nullValue())
        .body("decisions", hasSize(1))
        .body("decisions[0].reason", equalTo("실적을 확인한 보유 판단"))
        .body("decisions[0].reviews", hasSize(1))
        .body("email", nullValue())
        .body("name", nullValue());
    client(viewer).get(detail).then().statusCode(200).body("decisions", hasSize(1));
    client(owner)
        .body(
            "{\"profilePublic\":true,\"leagueEnabled\":true,\"allocationPublic\":true,\"detailPublic\":false}")
        .put("/api/users/me/portfolio/publication")
        .then()
        .statusCode(200);
    given().port(port).get(detail).then().statusCode(404);
    given()
        .port(port)
        .get("/api/league/investors/" + publicId)
        .then()
        .statusCode(200)
        .body("detailAvailable", equalTo(false));
    // 이전 클라이언트의 상세 공개 동의도 보존하고 응답은 새 이름만 사용한다.
    client(owner)
        .body(
            "{\"profilePublic\":true,\"leagueEnabled\":true,\"allocationPublic\":true,\"premiumDetailEnabled\":true}")
        .put("/api/users/me/portfolio/publication")
        .then()
        .statusCode(200)
        .body("detailPublic", equalTo(true))
        .body("premiumDetailEnabled", nullValue());

    // 집계 검증용 과거 이력은 격리된 Testcontainers DB에서만 만든다.
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusWeeks(1).with(DayOfWeek.MONDAY);
    Instant past = date.minusDays(10).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
    jdbc.update(
        "UPDATE public_investor_profiles SET league_enabled_at=? WHERE member_id=?",
        java.sql.Timestamp.from(past),
        owner.getId());
    jdbc.update(
        "UPDATE shared_holding_histories SET created_at=? WHERE portfolio_id=(SELECT id FROM shared_portfolios WHERE member_id=?)",
        java.sql.Timestamp.from(past),
        owner.getId());
    jdbc.update(
        "UPDATE portfolio_transactions SET traded_on=?,created_at=? WHERE id=?",
        java.sql.Date.valueOf(date),
        java.sql.Timestamp.from(past),
        id);
    jdbc.update(
        "UPDATE investment_reviews SET created_at=? WHERE transaction_id=?",
        java.sql.Timestamp.from(past),
        id);
    prices.save(new StockDailyPrice(asset, date.minusDays(1), new BigDecimal("70000")));
    prices.save(new StockDailyPrice(asset, date, new BigDecimal("77000")));
    leagueReturns.recalculate();
    given()
        .port(port)
        .queryParam("weekStart", date.toString())
        .get("/api/league/rankings")
        .then()
        .statusCode(200)
        .body("rankings.publicId", hasItem(publicId));
    String dailyHistory = "/api/league/investors/" + publicId + "/daily-history";
    var daily = given().port(port).queryParam("weekStart", date.toString()).get(dailyHistory);
    daily
        .then()
        .statusCode(200)
        .header("Cache-Control", "no-store")
        .body("weekStart", equalTo(date.toString()))
        .body("weekEnd", equalTo(date.plusDays(6).toString()))
        .body("asOfDate", equalTo(date.toString()))
        .body("weeklyReturnRate", equalTo(10.0f))
        .body("days.find { it.baseDate == '" + date + "' }.dailyReturnRate", equalTo(10.0f))
        .body("days.find { it.baseDate == '" + date + "' }.cumulativeReturnRate", equalTo(10.0f))
        .body("days.find { it.baseDate == '" + date + "' }.status", equalTo("CALCULATED"))
        .body("$", not(hasKey("email")))
        .body("$", not(hasKey("name")));
    assertThat(daily.asString())
        .doesNotContain(
            "startingEvaluationAmount", "endingEvaluationAmount", "quantity", owner.getEmail());
    given()
        .port(port)
        .queryParam("weekStart", date.minusWeeks(8).toString())
        .get(dailyHistory)
        .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_LEAGUE_WEEK"));
    given()
        .port(port)
        .queryParam("weekStart", "invalid-date")
        .get(dailyHistory)
        .then()
        .statusCode(400);
    client(viewer)
        .get(detail)
        .then()
        .statusCode(200)
        .body("decisions", hasSize(1))
        .body("decisions[0].transactionType", equalTo("HOLD"))
        .body("decisions[0].reviews", hasSize(1));
    client(viewer).delete("/api/users/me/league/follows/" + publicId).then().statusCode(204);
    client(viewer)
        .get("/api/users/me/league/follows/summary")
        .then()
        .statusCode(200)
        .body("investors", hasSize(0));
    client(owner)
        .body("{\"hidden\":true}")
        .patch(SHARED + "/holdings/" + asset.getId() + "/visibility")
        .then()
        .statusCode(200);
    client(viewer)
        .get(detail)
        .then()
        .statusCode(200)
        .body("holdings", hasSize(0))
        .body("decisions", hasSize(0));
    client(owner)
        .body(
            "{\"profilePublic\":false,\"leagueEnabled\":false,\"allocationPublic\":false,\"detailPublic\":false}")
        .put("/api/users/me/portfolio/publication")
        .then()
        .statusCode(200);
    given().port(port).get("/api/league/investors/" + publicId).then().statusCode(404);
    given().port(port).get(detail).then().statusCode(404);
    given().port(port).get(dailyHistory).then().statusCode(404);
    assertThat(
            originalHoldings
                .findByPortfolioIdAndAssetId(original.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(10);
  }

  @Test
  void realAccountCookiesLoadTheProfileAndLogoutRevokesRefreshWithoutDeletingSavedData() {
    Member owner = member();
    Asset asset = asset();
    var pair = tokenService.issue(owner.getId());
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .cookie("access_token", pair.accessToken())
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("id", equalTo(owner.getId().intValue()))
        .body("nickname", equalTo(owner.getNickname()));
    client(owner)
        .body(
            """
        {"assetId":%d,"quantity":10,"averagePurchasePrice":70000}
        """
                .formatted(asset.getId()))
        .post("/api/users/me/portfolio/holdings")
        .then()
        .statusCode(201);
    var logout =
        given()
            .port(port)
            .cookies(csrf.cookies())
            .cookie("access_token", pair.accessToken())
            .cookie("refresh_token", pair.refreshToken())
            .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
            .post("/api/auth/logout");
    logout.then().statusCode(204);
    assertThat(logout.header("Set-Cookie")).contains("Max-Age=0");
    given().port(port).get("/api/users/me").then().statusCode(401);
    given()
        .port(port)
        .cookies(csrf.cookies())
        .cookie("refresh_token", pair.refreshToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .post("/api/auth/refresh")
        .then()
        .statusCode(401);
    client(owner)
        .get("/api/users/me/portfolio")
        .then()
        .statusCode(200)
        .body("holdings", hasSize(1))
        .body("holdings[0].assetId", equalTo(asset.getId().intValue()))
        .body("holdings[0].quantity", equalTo(10));
  }

  @Test
  void serviceNicknamePersistsAcrossLoginAndPublicProfileNeverExposesPrivateNameOrEmail() {
    Member owner = member();
    Member other = member();
    String nickname = "별명" + UUID.randomUUID().toString().substring(0, 12);
    String publicNickname = "공개" + UUID.randomUUID().toString().substring(0, 12);
    client(owner)
        .body(
            """
        {"displayName":"%s","experienceLevel":"BEGINNER","riskProfile":"BALANCED","bio":""}
        """
                .formatted(publicNickname))
        .put("/api/users/me/investor-profile")
        .then()
        .statusCode(200);
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .body("{\"nickname\":\"" + nickname + "\"}")
        .patch("/api/users/me")
        .then()
        .statusCode(401);
    given()
        .port(port)
        .contentType("application/json")
        .cookie(
            "access_token", tokens.createTokenPair(owner.getId(), MemberRole.USER).accessToken())
        .body("{\"nickname\":\"" + nickname + "\"}")
        .patch("/api/users/me")
        .then()
        .statusCode(403);
    client(owner)
        .body("{\"nickname\":\"  " + nickname + "  \"}")
        .patch("/api/users/me")
        .then()
        .statusCode(200)
        .body("nickname", equalTo(nickname));
    client(owner)
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("nickname", equalTo(nickname))
        .body("email", equalTo(owner.getEmail()));
    assertThat(
            memberService
                .findOrCreateGoogleMember(
                    owner.getProviderId(), owner.getEmail(), "다른 Google 이름", null)
                .getNickname())
        .isEqualTo(nickname);
    client(owner)
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("name", equalTo("다른 Google 이름"))
        .body("nickname", equalTo(nickname));
    client(owner)
        .body("{\"nickname\":\"" + nickname + "\"}")
        .patch("/api/users/me")
        .then()
        .statusCode(200);
    client(other)
        .body("{\"nickname\":\"" + nickname + "\"}")
        .patch("/api/users/me")
        .then()
        .statusCode(409)
        .body("code", equalTo("NICKNAME_ALREADY_EXISTS"));
    client(other)
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("nickname", equalTo(other.getNickname()));
    client(owner)
        .get("/api/users/me/investor-profile")
        .then()
        .statusCode(200)
        .body("displayName", equalTo(nickname));
    client(owner)
        .body(
            """
        {"profilePublic":true,"leagueEnabled":false,"allocationPublic":false,"detailPublic":false}
        """)
        .put("/api/users/me/portfolio/publication")
        .then()
        .statusCode(200);
    String publicId =
        client(owner).get("/api/users/me/investor-profile").jsonPath().getString("publicId");
    given()
        .port(port)
        .get("/api/league/investors/" + publicId)
        .then()
        .statusCode(200)
        .body("displayName", equalTo(nickname))
        .body("$", not(hasKey("name")))
        .body("$", not(hasKey("email")));
  }

  @Test
  void concurrentNicknameUpdatesHaveOneWinnerAndOneConflictWithoutOverwritingEitherAccount()
      throws Exception {
    Member first = member();
    Member second = member();
    String nickname = "동시" + UUID.randomUUID().toString().substring(0, 12);
    var firstClient = client(first);
    var secondClient = client(second);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstRequest =
          executor.submit(
              () -> {
                start.await();
                return firstClient
                    .body("{\"nickname\":\"" + nickname + "\"}")
                    .patch("/api/users/me");
              });
      var secondRequest =
          executor.submit(
              () -> {
                start.await();
                return secondClient
                    .body("{\"nickname\":\"" + nickname + "\"}")
                    .patch("/api/users/me");
              });
      start.countDown();
      var a = firstRequest.get(15, TimeUnit.SECONDS);
      var b = secondRequest.get(15, TimeUnit.SECONDS);
      assertThat(java.util.List.of(a.statusCode(), b.statusCode()))
          .containsExactlyInAnyOrder(200, 409);
      var rejected = a.statusCode() == 409 ? a : b;
      assertThat(rejected.jsonPath().getString("code")).isEqualTo("NICKNAME_ALREADY_EXISTS");
      Member loser = a.statusCode() == 409 ? first : second;
      assertThat(members.findById(loser.getId()).orElseThrow().getNickname())
          .isEqualTo(loser.getNickname());
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM members WHERE nickname=?", Long.class, nickname))
          .isEqualTo(1L);
    }
  }

  @Test
  void sameGoogleNameCanRegisterConcurrentlyAndRepeatedLoginPreservesTheServiceNickname()
      throws Exception {
    String providerA = UUID.randomUUID().toString();
    String providerB = UUID.randomUUID().toString();
    String nickname = "가입" + UUID.randomUUID().toString().substring(0, 12);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> {
                start.await();
                return memberService.findOrCreateGoogleMember(
                    providerA, providerA + "@example.invalid", nickname, null);
              });
      var b =
          executor.submit(
              () -> {
                start.await();
                return memberService.findOrCreateGoogleMember(
                    providerB, providerB + "@example.invalid", nickname, null);
              });
      start.countDown();
      Member first = a.get(15, TimeUnit.SECONDS);
      Member second = b.get(15, TimeUnit.SECONDS);
      assertThat(first.getId()).isNotEqualTo(second.getId());
      assertThat(first.getNickname()).isNotEqualTo(second.getNickname());
      assertThat(first.getGoogleName()).isEqualTo(nickname);
      assertThat(second.getGoogleName()).isEqualTo(nickname);
      assertThat(first.getNickname()).startsWith("회원_");
      assertThat(second.getNickname()).startsWith("회원_");
      assertThat(first.getNickname().length()).isBetween(1, 20);
      assertThat(second.getNickname().length()).isBetween(1, 20);
      assertThat(
              memberService
                  .findOrCreateGoogleMember(providerA, first.getEmail(), "변경된 Google 이름", null)
                  .getNickname())
          .isEqualTo(first.getNickname());
    }
  }

  @Test
  void unauthenticatedAndInvalidChangesAreRejectedRatherThanReportedAsSaved() {
    given().port(port).get(SHARED).then().statusCode(401);
    Member owner = member();
    given()
        .port(port)
        .contentType("application/json")
        .cookie(
            "access_token", tokens.createTokenPair(owner.getId(), MemberRole.USER).accessToken())
        .body("{}")
        .post(SHARED + "/import")
        .then()
        .statusCode(403);
    client(owner).body("{\"holdings\":[]}").post(SHARED + "/import").then().statusCode(400);
    client(owner).body("{\"holdings\":[null]}").post(SHARED + "/import").then().statusCode(400);
    client(owner).body("{}").patch(SHARED + "/holdings/1/visibility").then().statusCode(400);
    client(owner).get(SHARED).then().statusCode(200).body("holdings", hasSize(0));
  }

  @Test
  void migrationPreservesOriginalAndLegacyLeagueRecordsAndRebindsTheirForeignKeys()
      throws Exception {
    String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("23")
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      connection.setSchema(schema);
      sql.execute(
          """
          INSERT INTO members(id, provider, provider_id, email, nickname, role, status, created_at, updated_at)
          VALUES (1,'GOOGLE','migration1','migration1@example.invalid','migration1','USER','ACTIVE',NOW(),NOW()),
                 (2,'GOOGLE','migration2','migration2@example.invalid','migration2','USER','ACTIVE',NOW(),NOW());
          INSERT INTO assets(id,name,category,asset_code,listed,created_at,updated_at)
          VALUES(1,'마이그레이션 종목','STOCK','MIGRATION',true,NOW(),NOW());
          INSERT INTO portfolios(id,member_id,created_at,updated_at)
          VALUES(10,1,NOW(),NOW()),(20,2,NOW(),NOW());
          INSERT INTO portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price,created_at,updated_at)
          VALUES(10,1,2,100,NOW(),NOW()),(20,1,3,200,NOW(),NOW());
          INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,created_at,updated_at)
          VALUES(10,1,'ADDED',2,100,NOW(),NOW());
          INSERT INTO portfolio_transactions(portfolio_id,asset_id,request_key,transaction_type,quantity,unit_price,traded_on,
            decision_reason,expected_holding_period,expected_change,invalidation_condition,maximum_acceptable_loss_rate,created_at,updated_at)
          VALUES(10,1,'migration','BUY',2,100,CURRENT_DATE,'기존 판단','OVER_SIX_MONTHS','성장','감소',10,NOW(),NOW());
          INSERT INTO portfolio_daily_returns(portfolio_id,base_date,eligible,calculated_at,created_at,updated_at)
          VALUES(10,CURRENT_DATE,false,NOW(),NOW(),NOW());
          """);
      Flyway.configure()
          .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
          .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      try (var result =
          sql.executeQuery(
              """
          SELECT (SELECT COUNT(*) FROM portfolios) AS originals,
                 (SELECT COUNT(*) FROM shared_portfolios) AS shared,
                 (SELECT COUNT(*) FROM shared_holding_histories) AS histories,
                 (SELECT quantity FROM portfolio_holdings WHERE portfolio_id=10) AS quantity,
                 (SELECT decision_reason FROM portfolio_transactions WHERE portfolio_id=10) AS reason
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt("originals")).isEqualTo(2);
        assertThat(result.getInt("shared")).isEqualTo(1);
        assertThat(result.getInt("histories")).isEqualTo(1);
        assertThat(result.getLong("quantity")).isEqualTo(2);
        assertThat(result.getString("reason")).isEqualTo("기존 판단");
      }
      sql.executeUpdate("DELETE FROM portfolios WHERE id=10");
      try (var result =
          sql.executeQuery(
              """
          SELECT (SELECT COUNT(*) FROM portfolio_transactions) AS transactions,
                 (SELECT COUNT(*) FROM portfolio_daily_returns) AS returns,
                 (SELECT COUNT(*) FROM shared_holdings) AS holdings
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt("transactions")).isEqualTo(1);
        assertThat(result.getInt("returns")).isEqualTo(1);
        assertThat(result.getInt("holdings")).isEqualTo(1);
      }
      try (var result =
          sql.executeQuery(
              """
          INSERT INTO shared_portfolios(member_id,created_at,updated_at)
          VALUES(2,NOW(),NOW()) RETURNING id
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isGreaterThan(10);
      }
    }
  }

  @Test
  void featureMigrationsPreserveRecordsRelationsAndIdentitySequencesOnRerun() throws Exception {
    String schema = "naming_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("26")
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      connection.setSchema(schema);
      // 기능명으로 생성한 테이블의 기록과 관계가 마이그레이션 재실행 후에도 유지된다.
      sql.execute(
          """
          INSERT INTO members(id,provider,provider_id,email,nickname,role,status,created_at,updated_at)
          VALUES(1,'GOOGLE','naming','naming@example.invalid','naming','USER','ACTIVE',NOW(),NOW());
          INSERT INTO assets(id,name,category,asset_code,listed,created_at,updated_at)
          VALUES(1,'이전 종목','STOCK','NAMING',true,NOW(),NOW());
          INSERT INTO learning_progress(member_id,item_key,item_type,completed,bookmarked,created_at,updated_at)
          VALUES(1,'guide-first-account','GUIDE',true,true,NOW(),NOW());
          INSERT INTO weekly_check_ins(member_id,week_start,target_savings,target_investment,target_debt_payment,
            actual_savings,actual_investment,actual_debt_payment,note,created_at,updated_at)
          VALUES(1,'2026-10-05',100,20,0,90,10,0,'기존 실천',NOW(),NOW());
          INSERT INTO simulated_trades(member_id,asset_id,trade_type,quantity,price,reason,traded_on,created_at,updated_at)
          VALUES(1,1,'BUY',2,100,'이전 기록',CURRENT_DATE,NOW(),NOW());
          INSERT INTO financial_plans(member_id,plan_key,content,created_at,updated_at)
          VALUES(1,'monthly-plan','{"title":"기존 계획"}',NOW(),NOW());
          INSERT INTO shared_portfolios(member_id,created_at,updated_at)
          VALUES(1,NOW(),NOW());
          INSERT INTO shared_holdings(portfolio_id,asset_id,quantity,average_purchase_price,hidden,created_at,updated_at)
          VALUES(1,1,3,200,true,NOW(),NOW());
          INSERT INTO shared_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,created_at,updated_at)
          VALUES(1,1,'ADDED',3,200,NOW(),NOW());
          """);
      Flyway.configure()
          .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
          .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      try (var result =
          sql.executeQuery(
              """
          SELECT (SELECT completed AND bookmarked FROM learning_progress) AS progress,
                 (SELECT note FROM weekly_check_ins) AS note,
                 (SELECT quantity FROM simulated_trades) AS trade_quantity,
                 (SELECT content->>'title' FROM financial_plans) AS title,
                 (SELECT hidden FROM shared_holdings) AS hidden,
                 (SELECT quantity FROM shared_holding_histories) AS history_quantity
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean("progress")).isTrue();
        assertThat(result.getString("note")).isEqualTo("기존 실천");
        assertThat(result.getLong("trade_quantity")).isEqualTo(2);
        assertThat(result.getString("title")).isEqualTo("기존 계획");
        assertThat(result.getBoolean("hidden")).isTrue();
        assertThat(result.getLong("history_quantity")).isEqualTo(3);
      }
      try (var result =
          sql.executeQuery(
              """
          INSERT INTO learning_progress(member_id,item_key,item_type,created_at,updated_at)
          VALUES(1,'next-guide','GUIDE',NOW(),NOW()) RETURNING id
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isEqualTo(2);
      }
      sql.executeUpdate("DELETE FROM shared_portfolios WHERE member_id=1");
      try (var result =
          sql.executeQuery(
              """
          SELECT (SELECT COUNT(*) FROM shared_holdings) +
                 (SELECT COUNT(*) FROM shared_holding_histories)
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isZero();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"19", "21"})
  void deployedVersionsUpgradeWithoutChangingExistingStockContent(String version) throws Exception {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target(version)
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      connection.setSchema(schema);
      sql.execute(
          """
          INSERT INTO assets(id,name,category,asset_code,listed,created_at,updated_at)
          VALUES(1,'기존 종목','STOCK','EXISTING',true,NOW(),NOW());
          INSERT INTO stock_related_contents(asset_id,source_type,title,url,image_url,created_at,updated_at)
          VALUES(1,'INTERNAL_NEWS','기존 콘텐츠','https://example.invalid/article','https://example.invalid/image',NOW(),NOW());
          """);
      Flyway.configure()
          .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
          .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      try (var result =
          sql.executeQuery("SELECT title,image_url FROM stock_related_contents WHERE asset_id=1")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("title")).isEqualTo("기존 콘텐츠");
        assertThat(result.getString("image_url")).isEqualTo("https://example.invalid/image");
      }
      try (var result =
          sql.executeQuery(
              """
          SELECT (SELECT COUNT(*) FROM pg_indexes WHERE schemaname=current_schema()
                    AND indexname IN ('idx_news_status_published_id','idx_news_created_id')) AS indexes,
                 (SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version::integer BETWEEN 22 AND 26) AS feature_migrations,
                 (SELECT MAX(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL) AS latest_version
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt("indexes")).isEqualTo(2);
        assertThat(result.getInt("feature_migrations")).isEqualTo(5);
        assertThat(result.getInt("latest_version")).isEqualTo(26);
      }
    }
  }
}
