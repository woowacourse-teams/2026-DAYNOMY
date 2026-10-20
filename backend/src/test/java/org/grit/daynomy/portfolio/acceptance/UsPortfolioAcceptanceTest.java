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

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  org.grit.daynomy.external.eodhd.EodhdClient provider;

  @Autowired org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository rates;
  @Autowired org.grit.daynomy.portfolio.repository.PortfolioRepository portfolios;
  @Autowired org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository holdings;
  @Autowired org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository histories;
  @Autowired org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository snapshots;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @BeforeEach
  void externalRateRequestsNeverHoldATransaction() {
    org.mockito.Mockito.doAnswer(
            invocation -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return invocation.callRealMethod();
            })
        .when(provider)
        .getUsdKrwRates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void stockAndEtfTradesPersistUsdPricesAndRatesAndRecalculateKrwCostAndProfit() {
    int initialRequests = UPSTREAM.getRequestCount();
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
              .body("holding.averagePurchasePriceCurrency", equalTo("KRW"))
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
          .body("holdings[0].averagePurchasePriceCurrency", equalTo("KRW"))
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
          .body("averagePurchasePriceCurrency", everyItem(equalTo("KRW")))
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
    assertThat(UPSTREAM.getRequestCount() - initialRequests).isEqualTo(4);
  }

  @Test
  void concurrentFirstQuotesUseAtomicUpsertWithoutDuplicateKeyFailures() throws Exception {
    var date = LocalDate.of(2020, 9, 1);
    rates.deleteById(date);
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return invocation.callRealMethod();
            })
        .when(provider)
        .getUsdKrwRates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    for (int i = 0; i < 2; i++)
      UPSTREAM.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody("[{\"date\":\"2020-09-01\",\"close\":1300}]"));
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> exchangeRates.quote(date));
      var second = executor.submit(() -> exchangeRates.quote(date));
      assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS).rate())
          .isEqualByComparingTo("1300");
      assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS).rate())
          .isEqualByComparingTo("1300");
    }
    assertThat(rates.findById(date)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from usd_krw_rates where requested_date = ?", Integer.class, date))
        .isEqualTo(1);
    // A late response from an earlier request must not overwrite a newer value.
    rates.upsert(
        date, date, new BigDecimal("1400"), java.time.Instant.parse("2030-01-01T00:00:00Z"));
    rates.upsert(
        date, date, new BigDecimal("1200"), java.time.Instant.parse("2029-01-01T00:00:00Z"));
    assertThat(rates.findById(date).orElseThrow().getRate()).isEqualByComparingTo("1400");
  }

  @Test
  void providerFailurePreservesLedgerAndSnapshotsForV1V2UpdateAndDelete() {
    var date = LocalDate.of(2020, 8, 3);
    String key = java.util.UUID.randomUUID().toString();
    var member =
        members.saveAndFlush(
            Member.createGoogleMember(key, key + "@example.invalid", "review", null));
    var asset =
        assets.saveAndFlush(Asset.listedStock("Review US", key, StockMarket.NASDAQ, null, date));
    prices.saveAndFlush(new StockDailyPrice(asset, date, new BigDecimal("100")));
    var portfolio =
        portfolios.saveAndFlush(org.grit.daynomy.portfolio.domain.Portfolio.create(member));
    holdings.saveAndFlush(
        new org.grit.daynomy.portfolio.domain.PortfolioHolding(
            portfolio, asset, 1, new BigDecimal("130000")));
    var history =
        new org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory(
            portfolio,
            asset,
            org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType.BUY,
            0,
            BigDecimal.ZERO,
            date,
            1L,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    history.applyExchangeRate(new BigDecimal("1300"));
    histories.saveAndFlush(history);
    var snapshot = new org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot(portfolio, date);
    snapshot.update(
        new BigDecimal("130000"),
        new BigDecimal("130000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null);
    snapshots.saveAndFlush(snapshot);
    var payload =
        Map.of(
            "assetId",
            asset.getId(),
            "type",
            "BUY",
            "tradeDate",
            date.toString(),
            "quantity",
            1,
            "unitPrice",
            100,
            "exchangeRate",
            1300,
            "fee",
            0,
            "tax",
            0);
    for (String path :
        java.util.List.of(
            "/api/users/me/portfolio/transactions", "/api/v2/users/me/portfolio/transactions")) {
      UPSTREAM.enqueue(new MockResponse().setResponseCode(503));
      client(member)
          .body(payload)
          .post(path)
          .then()
          .statusCode(502)
          .body("code", equalTo("EODHD_API_REQUEST_FAILED"));
    }
    UPSTREAM.enqueue(new MockResponse().setResponseCode(503));
    client(member)
        .body(
            Map.of(
                "tradeDate",
                date.toString(),
                "quantity",
                2,
                "unitPrice",
                100,
                "exchangeRate",
                1300,
                "fee",
                0,
                "tax",
                0))
        .patch("/api/portfolio/transactions/" + history.getId())
        .then()
        .statusCode(502)
        .body("code", equalTo("EODHD_API_REQUEST_FAILED"));
    UPSTREAM.enqueue(new MockResponse().setResponseCode(503));
    client(member)
        .delete("/api/portfolio/transactions/" + history.getId())
        .then()
        .statusCode(502)
        .body("code", equalTo("EODHD_API_REQUEST_FAILED"));
    assertThat(histories.findOrderedWithAssetsByPortfolioId(portfolio.getId())).hasSize(1);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(1);
    assertThat(
            snapshots
                .findByPortfolioIdAndBaseDate(portfolio.getId(), date)
                .orElseThrow()
                .getTotalEvaluationAmount())
        .isEqualByComparingTo("130000");
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
