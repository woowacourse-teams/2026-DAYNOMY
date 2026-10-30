package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
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
class PortfolioAuthorizationAcceptanceTest {

  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 9, 28);

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
  @Autowired private AssetRepository assets;
  @Autowired private StockDailyPriceRepository prices;
  @Autowired private JwtTokenProvider tokens;

  @ParameterizedTest
  @ValueSource(strings = {"/api/portfolio", "/api/users/me/portfolio"})
  void otherMembersAndAnonymousRequestsCannotChangeAnOwnersLedger(String path) {
    Member owner = member();
    Member other = member();
    String code = UUID.randomUUID().toString();
    Asset asset =
        assets.save(
            Asset.listedStock(
                "인가 검증 종목",
                code,
                StockMarket.KOSPI,
                "KR" + code.replace("-", "").substring(0, 10),
                TRADE_DATE.minusDays(1)));
    prices.saveAndFlush(new StockDailyPrice(asset, TRADE_DATE, new BigDecimal("110")));
    long buy = trade(owner, path, asset, "BUY", 5, 100);
    long sell = trade(owner, path, asset, "SELL", 1, 130);
    // B도 같은 종목을 보유하게 하여 포트폴리오가 없어서 거부되는 경우와 구분한다.
    trade(other, path, asset, "BUY", 3, 60);
    var ownerBefore = ledger(owner, path);
    var otherBefore = ledger(other, path);
    assertThat(new BigDecimal(ownerBefore.getFirst().get("totalRealizedProfitLoss").toString()))
        .isEqualByComparingTo("30");

    client(other).get(path).then().statusCode(200).body("holdings[0].quantity", equalTo(3));
    given().port(port).get(path).then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));

    for (String method : List.of("PATCH", "DELETE")) {
      var anonymous = anonymousWithCsrf();
      if (method.equals("PATCH")) anonymous.body(transaction(9, 1));
      anonymous
          .request(method, path + "/transactions/" + buy)
          .then()
          .statusCode(401)
          .body("code", equalTo("UNAUTHORIZED"));
      assertThat(ledger(owner, path)).isEqualTo(ownerBefore);

      var attacker = client(other);
      if (method.equals("PATCH")) attacker.body(transaction(9, 1));
      attacker
          .request(method, path + "/transactions/" + buy)
          .then()
          .statusCode(404)
          .body("code", equalTo("PORTFOLIO_TRANSACTION_NOT_FOUND"));
      // 거부 직후 HTTP로 거래 이력·보유수량·실현손익·기간별 평가액을 다시 조회한다.
      assertThat(ledger(owner, path)).isEqualTo(ownerBefore);
      assertThat(ledger(other, path)).isEqualTo(otherBefore);
    }

    client(owner)
        .body(transaction(6, 100))
        .patch(path + "/transactions/" + buy)
        .then()
        .statusCode(200);
    client(owner).get(path).then().statusCode(200).body("holdings[0].quantity", equalTo(5));
    client(owner).delete(path + "/transactions/" + sell).then().statusCode(200);
    client(owner).delete(path + "/transactions/" + buy).then().statusCode(200);
    client(owner).get(path).then().statusCode(200).body("holdings", hasSize(0));
    assertThat(ledger(other, path)).isEqualTo(otherBefore);
  }

  private List<Map<String, Object>> ledger(Member member, String path) {
    var portfolio = client(member).get(path).then().statusCode(200).extract().jsonPath();
    var histories =
        client(member)
            .queryParam("from", TRADE_DATE.toString())
            .queryParam("to", TRADE_DATE.toString())
            .get(path + "/histories")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    var performance =
        client(member)
            .queryParam("from", TRADE_DATE.toString())
            .queryParam("to", TRADE_DATE.toString())
            .get(path + "/performance")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    return List.of(
        portfolio.getMap(""), Map.of("histories", histories.getList("")), performance.getMap(""));
  }

  private long trade(
      Member member, String path, Asset asset, String type, int quantity, int price) {
    var body = new HashMap<>(transaction(quantity, price));
    body.put("assetId", asset.getId());
    body.put("type", type);
    return client(member)
        .body(body)
        .post(path + "/transactions")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("transactionId");
  }

  private Map<String, Object> transaction(int quantity, int price) {
    return Map.of(
        "tradeDate",
        TRADE_DATE.toString(),
        "quantity",
        quantity,
        "unitPrice",
        price,
        "fee",
        0,
        "tax",
        0);
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private RequestSpecification client(Member member) {
    return anonymousWithCsrf()
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken());
  }

  private RequestSpecification anonymousWithCsrf() {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
