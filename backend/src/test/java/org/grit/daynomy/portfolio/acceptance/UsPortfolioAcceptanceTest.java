package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import okhttp3.mockwebserver.*;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.*;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.*;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "stock.us.sync.enabled=false",
      "stock.sync.enabled=false",
      "stock.price.sync.enabled=false"
    })
class UsPortfolioAcceptanceTest {
  @Container
  static final PostgreSQLContainer DB =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  static final MockWebServer UPSTREAM = startServer();

  static MockWebServer startServer() {
    try {
      var server = new MockWebServer();
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterAll
  static void close() throws IOException {
    UPSTREAM.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.driver-class-name", DB::getDriverClassName);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
    r.add("external.eodhd.base-url", () -> UPSTREAM.url("/api").toString());
    r.add("external.eodhd.api-token", () -> "test-token");
  }

  @LocalServerPort int port;
  @Autowired AssetRepository assets;
  @Autowired StockDailyPriceRepository prices;
  @Autowired MemberRepository members;
  @Autowired JwtTokenProvider tokens;
  @Autowired org.grit.daynomy.exchangerate.service.ExchangeRateService exchangeRates;

  @Test
  void stockAndEtfTradesPersistUsdPricesAndRatesAndRecalculateKrwCostAndProfit() {
    for (var date : java.util.List.of("2020-10-15", "2020-10-16")) {
      UPSTREAM.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody("[{\"date\":\"" + date + "\",\"close\":1330.0}]"));
      given()
          .port(port)
          .queryParam("date", date)
          .get("/api/exchange-rates/usd-krw")
          .then()
          .statusCode(200)
          .body("rate", equalTo(1330.0f));
    }
    for (var category : java.util.List.of(AssetCategory.STOCK, AssetCategory.ETF)) {
      var asset =
          assets.saveAndFlush(
              Asset.listedSecurity(
                  category == AssetCategory.STOCK ? "Tesla" : "Vanguard ETF",
                  category,
                  category == AssetCategory.STOCK ? "TSLA.US" : "VTI.US",
                  StockMarket.NASDAQ,
                  null,
                  LocalDate.of(2020, 10, 15)));
      for (var date : java.util.List.of(LocalDate.of(2020, 10, 15), LocalDate.of(2020, 10, 16)))
        prices.saveAndFlush(new StockDailyPrice(asset, date, new BigDecimal("110")));
      var member =
          members.saveAndFlush(
              Member.createGoogleMember(
                  "usd-" + category,
                  "usd-" + category + "@example.invalid",
                  "usd-" + category,
                  null));
      String path =
          category == AssetCategory.STOCK
              ? "/api/users/me/portfolio/transactions"
              : "/api/v2/users/me/portfolio/transactions";
      long buy =
          client(member)
              .body(body(asset.getId(), "BUY", "100.1234", "1300", "1", "0", 2))
              .post(path)
              .then()
              .statusCode(201)
              .body("holding.averagePurchasePrice", equalTo(130810.42f))
              .body("holding.currency", equalTo("USD"))
              .extract()
              .jsonPath()
              .getLong("transactionId");
      client(member)
          .body(
              Map.of(
                  "holdings",
                  java.util.List.of(
                      Map.of(
                          "assetId",
                          asset.getId(),
                          "quantity",
                          2,
                          "averagePurchasePrice",
                          130810.42))))
          .contentType("application/json")
          .post("/api/portfolio/calculate")
          .then()
          .statusCode(200)
          .body("totalEvaluationAmount", equalTo(292600.0f))
          .body("holdings[0].closePrice", equalTo(110.0f));
      client(member)
          .queryParam("from", "2020-10-16")
          .queryParam("to", "2020-10-16")
          .get("/api/portfolio/performance")
          .then()
          .statusCode(200)
          .body("points[0].totalEvaluationAmount", equalTo(292600.0f))
          .body("points[0].totalPurchaseAmount", equalTo(261620.84f));
      long sell =
          client(member)
              .body(body(asset.getId(), "SELL", "110", "1400", "1", "0.5", 1))
              .post(path)
              .then()
              .statusCode(201)
              .body("holding.quantity", equalTo(1))
              .body("totalRealizedProfitLoss", equalTo(21089.58f))
              .extract()
              .jsonPath()
              .getLong("transactionId");
      // Daily reference-rate refresh must not rewrite the saved execution rates or costs.
      var rateDate = LocalDate.now(java.time.ZoneId.of("America/New_York")).minusDays(1);
      UPSTREAM.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody("[{\"date\":\"" + rateDate + "\",\"close\":1500.0}]"));
      assertThat(exchangeRates.refreshToday().rate()).isEqualByComparingTo("1500");
      client(member)
          .queryParam("from", "2020-10-15")
          .queryParam("to", "2020-10-16")
          .get("/api/portfolio/histories")
          .then()
          .statusCode(200)
          .body("currency", everyItem(equalTo("USD")))
          .body("exchangeRate", hasItems(1300.0f, 1400.0f))
          .body("unitPrice", hasItems(100.1234f, 110.0f));
      client(member)
          .body(
              Map.of(
                  "tradeDate",
                  "2020-10-16",
                  "quantity",
                  2,
                  "unitPrice",
                  100.1234,
                  "fee",
                  1,
                  "tax",
                  0,
                  "exchangeRate",
                  1350))
          .patch("/api/portfolio/transactions/" + buy)
          .then()
          .statusCode(200)
          .body("holding.averagePurchasePrice", equalTo(135841.59f))
          .body("totalRealizedProfitLoss", equalTo(16058.41f));
      client(member)
          .delete("/api/portfolio/transactions/" + sell)
          .then()
          .statusCode(200)
          .body("holding.quantity", equalTo(2))
          .body("totalRealizedProfitLoss", equalTo(0.0f));
      client(member)
          .body(body(asset.getId(), "SELL", "110", "1400", "0", "0", 3))
          .post(path)
          .then()
          .statusCode(400)
          .body("code", equalTo("INSUFFICIENT_PORTFOLIO_QUANTITY"));
    }
    assertThat(UPSTREAM.getRequestCount()).isEqualTo(4);
  }

  Map<String, Object> body(
      Long asset, String type, String price, String rate, String fee, String tax, int quantity) {
    return Map.of(
        "assetId",
        asset,
        "type",
        type,
        "tradeDate",
        "2020-10-16",
        "quantity",
        quantity,
        "unitPrice",
        new BigDecimal(price),
        "fee",
        new BigDecimal(fee),
        "tax",
        new BigDecimal(tax),
        "exchangeRate",
        new BigDecimal(rate));
  }

  io.restassured.specification.RequestSpecification client(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
