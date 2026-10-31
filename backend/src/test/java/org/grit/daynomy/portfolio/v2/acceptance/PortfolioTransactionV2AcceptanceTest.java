package org.grit.daynomy.portfolio.v2.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class PortfolioTransactionV2AcceptanceTest {
  private static final String V1 = "/api/users/me/portfolio/transactions";
  private static final String V2 = "/api/v2/users/me/portfolio/transactions";
  private static final LocalDate FIRST = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(45);
  private static final LocalDate LAST = FIRST.plusDays(40);

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
  @Autowired private PortfolioRepository portfolios;
  @Autowired private AssetRepository assets;
  @Autowired private StockDailyPriceRepository prices;
  @Autowired private PortfolioHoldingRepository holdings;
  @Autowired private PortfolioHoldingHistoryRepository histories;
  @Autowired private PortfolioDailySnapshotRepository snapshots;
  @Autowired private PortfolioSnapshotTransactionService originalSnapshots;
  @Autowired private JwtTokenProvider tokens;

  @ParameterizedTest
  @EnumSource(PortfolioTransactionType.class)
  void v1AndV2AgreeAcrossPriceBatchesWithFeesAndPreviouslySoldAssets(
      PortfolioTransactionType type) {
    Member v1 = member();
    Member v2 = member();
    Asset traded = asset();
    Asset sold = asset();
    priceRange(traded);
    priceRange(sold);
    Portfolio first = seed(v1, traded, sold);
    Portfolio second = seed(v2, traded, sold);
    var body = transaction(traded, type, FIRST.plusDays(1), 2);
    Map<String, Object> firstResponse =
        client(v1).body(body).post(V1).then().statusCode(201).extract().jsonPath().getMap("");
    Map<String, Object> secondResponse =
        client(v2).body(body).post(V2).then().statusCode(201).extract().jsonPath().getMap("");
    assertThat(((Number) secondResponse.get("transactionId")).longValue()).isPositive();
    assertThat(secondResponse.get("holding")).isEqualTo(firstResponse.get("holding"));
    assertThat(secondResponse.get("totalRealizedProfitLoss"))
        .isEqualTo(firstResponse.get("totalRealizedProfitLoss"));
    assertThat(points(second)).containsExactlyElementsOf(points(first));
    assertThat(points(second)).hasSize(41);
    // 현재는 전량 매도한 종목도 과거 시점의 평가에는 포함한다.
    assertThat(points(second).get(1).evaluation())
        .isGreaterThan(points(second).get(11).evaluation());
  }

  @Test
  void requiresAuthenticationAndCsrf() {
    Asset asset = asset();
    var body = transaction(asset, PortfolioTransactionType.BUY, FIRST, 1);
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .body(body)
        .post(V2)
        .then()
        .statusCode(401);
    Member member = member();
    given()
        .port(port)
        .contentType("application/json")
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken())
        .body(body)
        .post(V2)
        .then()
        .statusCode(403);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void recordsTradeTimeAndPreservesItWhenEditingWithTheExistingForm(String endpoint) {
    Member member = member();
    Asset asset = asset();
    priceRange(asset);
    var body = new HashMap<>(transaction(asset, PortfolioTransactionType.BUY, FIRST, 1));
    body.put("tradeTime", "09:35");
    long id =
        client(member)
            .body(body)
            .post(endpoint)
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("transactionId");
    String historyUrl = "/api/users/me/portfolio/histories?from=" + FIRST + "&to=" + LAST;
    assertThat(
            client(member)
                .get(historyUrl)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("[0].tradeTime"))
        .isEqualTo("09:35");
    var update = new HashMap<>(body);
    update.remove("tradeTime");
    update.put("quantity", 2);
    client(member).body(update).patch(V1 + "/" + id).then().statusCode(200);
    assertThat(
            client(member)
                .get(historyUrl)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("[0].tradeTime"))
        .isEqualTo("09:35");
    body.put("tradeTime", "25:70");
    client(member).body(body).post(endpoint).then().statusCode(400);
  }

  @Test
  void rejectsInvalidQuantityAndFutureTradeDateBeforeCreatingPortfolio() {
    Member member = member();
    Asset asset = asset();
    client(member)
        .body(transaction(asset, PortfolioTransactionType.BUY, FIRST, 0))
        .post(V2)
        .then()
        .statusCode(400);
    client(member)
        .body(
            transaction(
                asset,
                PortfolioTransactionType.BUY,
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1),
                1))
        .post(V2)
        .then()
        .statusCode(400);
    assertThat(portfolios.findByMemberId(member.getId())).isEmpty();
  }

  @Test
  void rejectsOversellAndTradeBeforeBalanceWithoutChangingSavedState() {
    Member member = member();
    Asset asset = asset();
    Portfolio portfolio = balance(member, asset, FIRST, 10, "100");
    client(member)
        .body(transaction(asset, PortfolioTransactionType.SELL, FIRST, 11))
        .post(V2)
        .then()
        .statusCode(400);
    client(member)
        .body(transaction(asset, PortfolioTransactionType.BUY, FIRST.minusDays(1), 1))
        .post(V2)
        .then()
        .statusCode(400);
    assertThat(histories.findOrderedWithAssetsByPortfolioId(portfolio.getId())).hasSize(1);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(10);
  }

  @Test
  void unrelatedAssetPriceDoesNotInventPreviousTradingDay() {
    Member member = member();
    Asset asset = asset();
    Portfolio portfolio = balance(member, asset, FIRST, 10, "100");
    Asset calendar = asset();
    prices.saveAndFlush(new StockDailyPrice(calendar, FIRST, new BigDecimal("100")));
    prices.saveAndFlush(new StockDailyPrice(asset, FIRST.plusDays(1), new BigDecimal("120")));
    PortfolioDailySnapshot stale = new PortfolioDailySnapshot(portfolio, FIRST.plusDays(1));
    stale.update(
        new BigDecimal("1000"),
        new BigDecimal("1200"),
        new BigDecimal("200"),
        new BigDecimal("20"),
        new BigDecimal("200"),
        new BigDecimal("20"));
    snapshots.saveAndFlush(stale);
    client(member)
        .body(transaction(asset, PortfolioTransactionType.BUY, FIRST, 1))
        .post(V2)
        .then()
        .statusCode(201);
    assertThat(snapshots.findByPortfolioIdAndBaseDate(portfolio.getId(), FIRST.plusDays(1)))
        .get()
        .extracting(PortfolioDailySnapshot::getTotalEvaluationAmount)
        .isEqualTo(new BigDecimal("1320.00"));
  }

  @Test
  void fullSaleRemovesHoldingAndAffectedSnapshots() {
    Member member = member();
    Asset asset = asset();
    priceRange(asset);
    Portfolio portfolio = balance(member, asset, FIRST, 10, "100");
    originalSnapshots.createSnapshot(portfolio.getId(), FIRST, FIRST.minusDays(1));
    var response =
        client(member)
            .body(transaction(asset, PortfolioTransactionType.SELL, FIRST, 10))
            .post(V2)
            .then()
            .statusCode(201)
            .extract()
            .jsonPath();
    assertThat((Object) response.get("holding")).isNull();
    assertThat(new BigDecimal(response.getString("totalRealizedProfitLoss")))
        .isEqualByComparingTo("97.53");
    assertThat(holdings.findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())).isEmpty();
    assertThat(points(portfolio)).isEmpty();
  }

  @Test
  void snapshotFailureRollsBackTradeHoldingAndEarlierBatchUpdates() {
    Member member = member();
    Asset asset = asset();
    priceRange(asset);
    Portfolio portfolio = balance(member, asset, FIRST, 10, "100");
    originalSnapshots.createSnapshot(portfolio.getId(), FIRST.plusDays(2), FIRST.plusDays(1));
    var before = points(portfolio);
    StockDailyPrice expensive =
        prices.findByAssetIdAndBaseDate(asset.getId(), FIRST.plusDays(35)).orElseThrow();
    expensive.synchronize(new BigDecimal("99999999999999999.99"));
    prices.saveAndFlush(expensive);
    client(member)
        .body(transaction(asset, PortfolioTransactionType.BUY, FIRST, 100))
        .post(V2)
        .then()
        .statusCode(500);
    assertThat(histories.findOrderedWithAssetsByPortfolioId(portfolio.getId())).hasSize(1);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(10);
    assertThat(points(portfolio)).containsExactlyElementsOf(before);
  }

  @Test
  void simultaneousFirstRegistrationsCreateOnePortfolioAndKeepBothTrades() throws Exception {
    Member member = member();
    Asset asset = asset();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    var body = transaction(asset, PortfolioTransactionType.BUY, today, 1);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> {
                            var request = client(member);
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS)) {
                              throw new IllegalStateException("동시 요청 대기 시간 초과");
                            }
                            return request
                                .body(body)
                                .post(V2)
                                .then()
                                .statusCode(201)
                                .extract()
                                .jsonPath()
                                .getLong("transactionId");
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(tasks.get(0).get(20, TimeUnit.SECONDS))
          .isNotEqualTo(tasks.get(1).get(20, TimeUnit.SECONDS));
    }
    Portfolio portfolio = portfolios.findByMemberId(member.getId()).orElseThrow();
    assertThat(histories.findOrderedWithAssetsByPortfolioId(portfolio.getId())).hasSize(2);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(2);
  }

  @Test
  void v2UsesOnlyPortfolioAssetDatesWhenAnotherMarketTradesOnInterveningDay() {
    Member member = member();
    Asset domestic = asset();
    Portfolio portfolio = balance(member, domestic, FIRST, 10, "100");
    prices.saveAndFlush(new StockDailyPrice(domestic, FIRST, new BigDecimal("100")));
    prices.saveAndFlush(new StockDailyPrice(domestic, FIRST.plusDays(2), new BigDecimal("120")));
    Asset us =
        assets.save(
            Asset.listedStock("US", UUID.randomUUID().toString(), StockMarket.NASDAQ, null, FIRST));
    prices.saveAndFlush(new StockDailyPrice(us, FIRST.plusDays(1), new BigDecimal("999")));
    client(member)
        .body(transaction(domestic, PortfolioTransactionType.BUY, FIRST, 1))
        .post(V2)
        .then()
        .statusCode(201);
    var result =
        snapshots.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
            portfolio.getId(), FIRST, LAST);
    assertThat(result)
        .extracting(PortfolioDailySnapshot::getBaseDate)
        .containsExactly(FIRST, FIRST.plusDays(2));
    assertThat(result.getLast().getDailyProfitLoss()).isEqualByComparingTo("220");
  }

  @Test
  void missingPreviousPriceOfAHeldAssetPreservesSnapshot() {
    Member member = member();
    Asset first = asset();
    Asset second = asset();
    Portfolio portfolio = balance(member, first, FIRST, 10, "100");
    opening(portfolio, second, FIRST, 1, "100");
    prices.saveAndFlush(new StockDailyPrice(second, FIRST, new BigDecimal("100")));
    prices.saveAndFlush(new StockDailyPrice(second, FIRST.plusDays(1), new BigDecimal("110")));
    prices.saveAndFlush(new StockDailyPrice(first, FIRST.plusDays(1), new BigDecimal("120")));
    var existing = new PortfolioDailySnapshot(portfolio, FIRST.plusDays(1));
    existing.update(
        new BigDecimal("1100"),
        new BigDecimal("1300"),
        new BigDecimal("200"),
        new BigDecimal("18.18"),
        null,
        null);
    snapshots.saveAndFlush(existing);
    client(member)
        .body(transaction(first, PortfolioTransactionType.BUY, FIRST, 1))
        .post(V2)
        .then()
        .statusCode(201);
    assertThat(
            snapshots
                .findByPortfolioIdAndBaseDate(portfolio.getId(), FIRST.plusDays(1))
                .orElseThrow()
                .getTotalEvaluationAmount())
        .isEqualByComparingTo("1300");
    assertThat(snapshots.findByPortfolioIdAndBaseDate(portfolio.getId(), FIRST)).isEmpty();
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.saveAndFlush(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private Asset asset() {
    String key = UUID.randomUUID().toString();
    return assets.saveAndFlush(
        Asset.listedStock(
            "V2 테스트 종목",
            key,
            StockMarket.KOSPI,
            "KR" + key.replace("-", "").substring(0, 10),
            FIRST));
  }

  private Portfolio balance(
      Member member, Asset asset, LocalDate day, long quantity, String average) {
    Portfolio portfolio = portfolios.saveAndFlush(Portfolio.create(member));
    opening(portfolio, asset, day, quantity, average);
    return portfolio;
  }

  private void opening(
      Portfolio portfolio, Asset asset, LocalDate day, long quantity, String average) {
    holdings.saveAndFlush(
        new PortfolioHolding(portfolio, asset, quantity, new BigDecimal(average)));
    histories.saveAndFlush(
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.OPENING_BALANCE,
            quantity,
            new BigDecimal(average),
            day,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }

  private Portfolio seed(Member member, Asset traded, Asset sold) {
    Portfolio portfolio = balance(member, traded, FIRST, 10, "100");
    opening(portfolio, sold, FIRST, 5, "50.55");
    histories.saveAndFlush(
        new PortfolioHoldingHistory(
            portfolio,
            sold,
            PortfolioHoldingChangeType.SELL,
            5,
            new BigDecimal("50.55"),
            FIRST.plusDays(10),
            5L,
            new BigDecimal("80.80"),
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    holdings.delete(
        holdings.findByPortfolioIdAndAssetId(portfolio.getId(), sold.getId()).orElseThrow());
    originalSnapshots.createSnapshot(portfolio.getId(), FIRST, FIRST.minusDays(1));
    return portfolio;
  }

  private void priceRange(Asset asset) {
    for (LocalDate day = FIRST.minusDays(1); !day.isAfter(LAST); day = day.plusDays(1)) {
      prices.saveAndFlush(
          new StockDailyPrice(
              asset,
              day,
              new BigDecimal("123.45")
                  .add(
                      BigDecimal.valueOf(day.toEpochDay() - FIRST.toEpochDay())
                          .multiply(new BigDecimal("0.25")))));
    }
  }

  private Map<String, Object> transaction(
      Asset asset, PortfolioTransactionType type, LocalDate day, int quantity) {
    return Map.of(
        "assetId",
        asset.getId(),
        "type",
        type.name(),
        "tradeDate",
        day.toString(),
        "quantity",
        quantity,
        "unitPrice",
        new BigDecimal("110.11"),
        "fee",
        new BigDecimal("1.23"),
        "tax",
        new BigDecimal("2.34"));
  }

  private List<Point> points(Portfolio portfolio) {
    return snapshots
        .findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(portfolio.getId(), FIRST, LAST)
        .stream()
        .map(
            snapshot ->
                new Point(
                    snapshot.getBaseDate(),
                    snapshot.getTotalPurchaseAmount(),
                    snapshot.getTotalEvaluationAmount(),
                    snapshot.getTotalProfitLoss(),
                    snapshot.getTotalReturnRate(),
                    snapshot.getDailyProfitLoss(),
                    snapshot.getDailyReturnRate()))
        .toList();
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

  private record Point(
      LocalDate date,
      BigDecimal purchase,
      BigDecimal evaluation,
      BigDecimal profit,
      BigDecimal rate,
      BigDecimal dailyProfit,
      BigDecimal dailyRate) {}
}
