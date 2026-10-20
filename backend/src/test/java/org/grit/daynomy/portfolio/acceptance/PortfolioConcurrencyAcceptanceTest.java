package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotTransactionService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
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
class PortfolioConcurrencyAcceptanceTest {

  private static final String PORTFOLIO = "/api/users/me/portfolio";
  private static final String TRANSACTIONS = PORTFOLIO + "/transactions";
  private static final String V2 = "/api/v2/users/me/portfolio/transactions";
  private static final LocalDate DATE = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(2);

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
  @Autowired private DataSource dataSource;
  @Autowired private MemberRepository members;
  @Autowired private PortfolioRepository portfolios;
  @Autowired private AssetRepository assets;
  @Autowired private StockDailyPriceRepository prices;
  @Autowired private PortfolioHoldingRepository holdings;
  @Autowired private PortfolioHoldingHistoryRepository histories;
  @Autowired private PortfolioDailySnapshotRepository snapshots;
  @Autowired private PortfolioLedgerCalculator calculator;
  @Autowired private PortfolioSnapshotTransactionService snapshotTransactions;
  @Autowired private JwtTokenProvider tokens;

  @RepeatedTest(10)
  void concurrentCorrectionsKeepLedgerHoldingAndSnapshotConsistent() throws Exception {
    Fixture fixture = fixture();
    var first = client(fixture.member()).body(correction(12));
    var second = client(fixture.member()).body(correction(20));

    List<Response> responses =
        overlap(
            fixture,
            () -> first.patch(TRANSACTIONS + "/" + fixture.firstTransaction()),
            () -> second.patch(TRANSACTIONS + "/" + fixture.secondTransaction()));

    assertThat(responses).extracting(Response::statusCode).containsExactly(200, 200);
    assertConsistent(fixture, 32);
  }

  @Test
  void correctionAndDeletionKeepRemainingTransactionAndSnapshotConsistent() throws Exception {
    Fixture fixture = fixture();
    var correction = client(fixture.member()).body(correction(12));
    var deletion = client(fixture.member());

    List<Response> responses =
        overlap(
            fixture,
            () -> correction.patch(TRANSACTIONS + "/" + fixture.firstTransaction()),
            () -> deletion.delete(TRANSACTIONS + "/" + fixture.secondTransaction()));

    assertThat(responses).extracting(Response::statusCode).containsExactly(200, 200);
    assertConsistent(fixture, 12);
  }

  @ParameterizedTest
  @ValueSource(strings = {TRANSACTIONS, V2})
  void correctionAndRegistrationShareTheSameLock(String endpoint) throws Exception {
    Fixture fixture = fixture();
    var correction = client(fixture.member()).body(correction(12));
    var registration = client(fixture.member()).body(trade(fixture.asset(), "BUY", 5));

    List<Response> responses =
        overlap(
            fixture,
            () -> correction.patch(TRANSACTIONS + "/" + fixture.firstTransaction()),
            () -> registration.post(endpoint));

    assertThat(responses).extracting(Response::statusCode).containsExactly(200, 201);
    assertConsistent(fixture, 27);
  }

  @ParameterizedTest
  @ValueSource(strings = {TRANSACTIONS, V2})
  void simultaneousSellsRejectOversellAndRollBackTheRejectedTransaction(String endpoint)
      throws Exception {
    Fixture fixture = fixture();
    var first = client(fixture.member()).body(trade(fixture.asset(), "SELL", 15));
    var second = client(fixture.member()).body(trade(fixture.asset(), "SELL", 15));

    List<Response> responses =
        overlap(fixture, () -> first.post(TRANSACTIONS), () -> second.post(endpoint));

    assertThat(responses).extracting(Response::statusCode).containsExactlyInAnyOrder(201, 400);
    assertThat(
            histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
                fixture.portfolio().getId()))
        .hasSize(3);
    assertConsistent(fixture, 5);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void balanceCorrectionOrRemovalAndRegistrationReplayTheSameHistory(boolean remove)
      throws Exception {
    Fixture fixture = fixture();
    var balance = client(fixture.member());
    if (!remove) {
      balance.body(Map.of("quantity", 25, "averagePurchasePrice", 100));
    }
    var body = new java.util.HashMap<String, Object>(trade(fixture.asset(), "BUY", 5));
    body.put("tradeDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
    var registration = client(fixture.member()).body(body);

    List<Response> responses =
        overlap(
            fixture,
            () ->
                remove
                    ? balance.delete(PORTFOLIO + "/holdings/" + fixture.asset().getId())
                    : balance.patch(PORTFOLIO + "/holdings/" + fixture.asset().getId()),
            () -> registration.post(V2));

    assertThat(responses).extracting(Response::statusCode).containsExactly(remove ? 204 : 200, 201);
    long expected = remove ? 5 : 30;
    Long portfolioId = fixture.portfolio().getId();
    assertThat(
            calculator
                .replay(histories.findOrderedWithAssetsByPortfolioId(portfolioId))
                .positions()
                .get(fixture.asset().getId())
                .quantity())
        .isEqualTo(expected);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, fixture.asset().getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(expected);
    // 오늘의 보유 정정은 과거 보유 수량을 바꾸지 않는다.
    assertThat(
            snapshots
                .findByPortfolioIdAndBaseDate(portfolioId, DATE)
                .orElseThrow()
                .getTotalEvaluationAmount())
        .isEqualByComparingTo("2000");
  }

  @Test
  void snapshotFailureRollsBackCorrectionHoldingAndEarlierSnapshotUpdates() {
    Fixture fixture = fixture();
    prices.saveAndFlush(
        new StockDailyPrice(
            fixture.asset(), DATE.plusDays(1), new BigDecimal("99999999999999999.99")));

    client(fixture.member())
        .body(correction(12))
        .patch(TRANSACTIONS + "/" + fixture.firstTransaction())
        .then()
        .statusCode(500);

    assertThat(
            histories.findById(fixture.firstTransaction()).orElseThrow().getTransactionQuantity())
        .isEqualTo(10);
    assertConsistent(fixture, 20);
    assertThat(
            snapshots.findByPortfolioIdAndBaseDate(fixture.portfolio().getId(), DATE.plusDays(1)))
        .isEmpty();
  }

  @Test
  void snapshotCreationAndCorrectionKeepOneSnapshotWithTheCommittedLedger() throws Exception {
    Fixture fixture = fixture();
    var correction = client(fixture.member()).body(correction(12));

    try (Connection blocker = lock(fixture);
        var executor = Executors.newFixedThreadPool(2)) {
      try {
        var update =
            executor.submit(
                () -> correction.patch(TRANSACTIONS + "/" + fixture.firstTransaction()));
        awaitBlockedRequests(1);
        var snapshot =
            executor.submit(
                () -> snapshotTransactions.createSnapshot(fixture.portfolio().getId(), DATE, null));
        awaitBlockedRequests(2);
        blocker.commit();
        assertThat(update.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        snapshot.get(10, TimeUnit.SECONDS);
      } finally {
        blocker.rollback();
      }
    }
    assertConsistent(fixture, 22);
  }

  @Test
  void aBlockedPortfolioDoesNotBlockAnotherMembersCorrection() throws Exception {
    Fixture blocked = fixture();
    Fixture independent = fixture();
    var first = client(blocked.member()).body(correction(12));
    var second = client(independent.member()).body(correction(12));

    try (Connection blocker = lock(blocked);
        var executor = Executors.newFixedThreadPool(2)) {
      try {
        var waiting =
            executor.submit(() -> first.patch(TRANSACTIONS + "/" + blocked.firstTransaction()));
        awaitBlockedRequests(1);
        var other =
            executor.submit(
                () -> second.patch(TRANSACTIONS + "/" + independent.firstTransaction()));
        assertThat(other.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        assertThat(waiting.isDone()).isFalse();
        blocker.commit();
        assertThat(waiting.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      } finally {
        blocker.rollback();
      }
    }
    assertConsistent(blocked, 22);
    assertConsistent(independent, 22);
  }

  // PostgreSQL이 실제로 두 요청을 잠금 대기시키는 것을 확인한 뒤 해제한다.
  // 기존 구현에서는 첫 요청이 스냅샷의 포트폴리오 잠금을, 둘째가 보유 행 잠금을 기다린다.
  private List<Response> overlap(
      Fixture fixture, Callable<Response> first, Callable<Response> second) throws Exception {
    try (Connection blocker = lock(fixture);
        var executor = Executors.newFixedThreadPool(2)) {
      try {
        var firstResponse = executor.submit(first);
        awaitBlockedRequests(1);
        var secondResponse = executor.submit(second);
        awaitBlockedRequests(2);
        blocker.commit();
        return List.of(
            firstResponse.get(10, TimeUnit.SECONDS), secondResponse.get(10, TimeUnit.SECONDS));
      } finally {
        blocker.rollback();
      }
    }
  }

  private Connection lock(Fixture fixture) throws Exception {
    Connection connection = dataSource.getConnection();
    connection.setAutoCommit(false);
    try (var query =
        connection.prepareStatement("select id from portfolios where id = ? for update")) {
      query.setLong(1, fixture.portfolio().getId());
      try (var result = query.executeQuery()) {
        assertThat(result.next()).isTrue();
      }
    }
    return connection;
  }

  private void awaitBlockedRequests(int expected) {
    await()
        .pollInterval(Duration.ofMillis(20))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              try (var connection = dataSource.getConnection();
                  var query = connection.createStatement();
                  var result =
                      query.executeQuery(
                          "select count(*) from pg_stat_activity where datname = current_database()"
                              + " and state = 'active' and wait_event_type = 'Lock'")) {
                result.next();
                assertThat(result.getInt(1)).isGreaterThanOrEqualTo(expected);
              }
            });
  }

  private void assertConsistent(Fixture fixture, long quantity) {
    Long portfolioId = fixture.portfolio().getId();
    var ledger = calculator.replay(histories.findOrderedWithAssetsByPortfolioId(portfolioId));
    assertThat(ledger.positions().get(fixture.asset().getId()).quantity()).isEqualTo(quantity);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, fixture.asset().getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(quantity);
    var points =
        snapshots.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(portfolioId, DATE, DATE);
    assertThat(points).hasSize(1);
    assertThat(points.getFirst().getTotalEvaluationAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(quantity * 100));
  }

  private Fixture fixture() {
    String key = UUID.randomUUID().toString();
    Member member =
        members.save(
            Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
    Asset asset =
        assets.save(
            Asset.listedStock(
                "동시성 종목",
                key,
                StockMarket.KOSPI,
                "KR" + key.replace("-", "").substring(0, 10),
                DATE.minusDays(1)));
    Portfolio portfolio = portfolios.save(Portfolio.create(member));
    holdings.save(new PortfolioHolding(portfolio, asset, 20, new BigDecimal("100")));
    var first = histories.saveAndFlush(buy(portfolio, asset));
    var second = histories.saveAndFlush(buy(portfolio, asset));
    prices.saveAndFlush(new StockDailyPrice(asset, DATE, new BigDecimal("100")));
    snapshotTransactions.createSnapshot(portfolio.getId(), DATE, null);
    return new Fixture(member, asset, portfolio, first.getId(), second.getId());
  }

  private PortfolioHoldingHistory buy(Portfolio portfolio, Asset asset) {
    return new PortfolioHoldingHistory(
        portfolio,
        asset,
        PortfolioHoldingChangeType.BUY,
        0,
        BigDecimal.ZERO,
        DATE,
        10L,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private Map<String, Object> correction(int quantity) {
    return Map.of(
        "tradeDate", DATE.toString(), "quantity", quantity, "unitPrice", 100, "fee", 0, "tax", 0);
  }

  private Map<String, Object> trade(Asset asset, String type, int quantity) {
    var body = new java.util.HashMap<String, Object>(correction(quantity));
    body.put("assetId", asset.getId());
    body.put("type", type);
    return body;
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

  private record Fixture(
      Member member,
      Asset asset,
      Portfolio portfolio,
      long firstTransaction,
      long secondTransaction) {}
}
