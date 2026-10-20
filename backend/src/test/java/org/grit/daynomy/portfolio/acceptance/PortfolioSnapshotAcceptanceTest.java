package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
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
class PortfolioSnapshotAcceptanceTest {

  private static final String PORTFOLIO = "/api/users/me/portfolio";
  private static final LocalDate FIRST = LocalDate.of(2026, 9, 28);
  private static final LocalDate SECOND = FIRST.plusDays(1);
  private static final LocalDate THIRD = FIRST.plusDays(2);

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
  @Autowired private PortfolioRepository portfolios;
  @Autowired private PortfolioHoldingRepository holdings;
  @Autowired private PortfolioHoldingHistoryRepository histories;
  @Autowired private PortfolioDailySnapshotRepository snapshots;
  @Autowired private PortfolioSnapshotService snapshotService;
  @Autowired private PortfolioSnapshotTransactionService snapshotTransactions;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private JwtTokenProvider tokens;

  @Test
  void missingPricesAreBackfilledAndCorrectionsAreVisibleWithoutDuplicateWrites() {
    Member member = member();
    Asset asset = asset();
    buy(member, asset, FIRST, 2, 100);
    Portfolio portfolio = portfolios.findByMemberId(member.getId()).orElseThrow();
    assertThat(points(portfolio)).isEmpty();

    price(asset, FIRST, 110);
    price(asset, SECOND, 120);
    priceSynced();
    assertThat(points(portfolio)).hasSize(2);
    assertPerformance(member, List.of(220f, 240f));
    PortfolioDailySnapshot before = points(portfolio).getLast();

    StockDailyPrice corrected =
        prices.findByAssetIdAndBaseDate(asset.getId(), SECOND).orElseThrow();
    corrected.synchronize(new BigDecimal("130"));
    prices.saveAndFlush(corrected);
    priceSynced();
    PortfolioDailySnapshot after = points(portfolio).getLast();
    assertThat(after.getId()).isEqualTo(before.getId());
    assertThat(after.getDailyProfitLoss()).isEqualByComparingTo("40");
    assertPerformance(member, List.of(220f, 260f));

    priceSynced();
    assertThat(points(portfolio)).hasSize(2);
    assertThat(points(portfolio).getLast().getUpdatedAt()).isEqualTo(after.getUpdatedAt());
  }

  @Test
  void backdatedTransactionsAndMovingOrDeletingThemRefreshEveryAffectedDate() {
    Member member = member();
    Asset asset = asset();
    price(asset, FIRST, 110);
    price(asset, SECOND, 120);
    price(asset, THIRD, 130);
    long laterTransaction = buy(member, asset, SECOND, 1, 100);
    long earlierTransaction = buy(member, asset, FIRST, 2, 80);
    Portfolio portfolio = portfolios.findByMemberId(member.getId()).orElseThrow();
    assertPerformance(member, List.of(220f, 360f, 390f));
    assertThat(points(portfolio).getLast().getTotalPurchaseAmount()).isEqualByComparingTo("260.01");

    client(member)
        .body(transaction(THIRD, 1, 200))
        .patch(PORTFOLIO + "/transactions/" + laterTransaction)
        .then()
        .statusCode(200);
    assertPerformance(member, List.of(220f, 240f, 390f));
    assertThat(points(portfolio).getLast().getTotalPurchaseAmount()).isEqualByComparingTo("360");

    client(member).delete(PORTFOLIO + "/transactions/" + earlierTransaction).then().statusCode(200);
    assertPerformance(member, List.of(130f));
    assertThat(points(portfolio))
        .extracting(PortfolioDailySnapshot::getBaseDate)
        .containsExactly(THIRD);

    client(member).delete(PORTFOLIO + "/transactions/" + laterTransaction).then().statusCode(200);
    assertThat(points(portfolio)).isEmpty();
    assertPerformance(member, List.of());
  }

  @Test
  void movingTransactionEarlierRestoresSnapshotsBeforeItsOldDate() {
    Member member = member();
    Asset asset = asset();
    price(asset, FIRST, 110);
    price(asset, SECOND, 120);
    price(asset, THIRD, 130);
    long transaction = buy(member, asset, SECOND, 1, 100);
    assertPerformance(member, List.of(120f, 130f));

    client(member)
        .body(transaction(FIRST, 2, 90))
        .patch(PORTFOLIO + "/transactions/" + transaction)
        .then()
        .statusCode(200);

    assertPerformance(member, List.of(220f, 240f, 260f));
    Portfolio portfolio = portfolios.findByMemberId(member.getId()).orElseThrow();
    assertThat(points(portfolio).getFirst().getTotalPurchaseAmount()).isEqualByComparingTo("180");
  }

  @Test
  void missingHoldingPricePreservesExistingSnapshotsUntilCompletePricesArrive() {
    Member member = member();
    Asset firstAsset = asset();
    Asset secondAsset = asset();
    price(firstAsset, FIRST, 110);
    price(firstAsset, SECOND, 120);
    buy(member, firstAsset, FIRST, 1, 100);
    buy(member, secondAsset, FIRST, 1, 100);
    Portfolio portfolio = portfolios.findByMemberId(member.getId()).orElseThrow();
    assertPerformance(member, List.of(110f, 120f));

    price(secondAsset, FIRST, 90);
    priceSynced();
    assertPerformance(member, List.of(200f, 120f));

    price(secondAsset, SECOND, 95);
    priceSynced();
    assertPerformance(member, List.of(200f, 215f));
  }

  @Test
  void existingLegacyHoldingsAreReplayedFromTheirRecordedDate() {
    Member member = member();
    Asset asset = asset();
    Portfolio portfolio = legacyHolding(member, asset, SECOND);
    price(asset, FIRST, 110);
    price(asset, SECOND, 120);
    price(asset, THIRD, 130);

    snapshotService.createLatestSnapshots();

    assertThat(points(portfolio))
        .extracting(PortfolioDailySnapshot::getBaseDate)
        .containsExactly(SECOND, THIRD);
    assertPerformance(member, List.of(240f, 260f));
    assertThat(snapshotService.createLatestSnapshots()).isZero();
  }

  @Test
  void concurrentSnapshotCreationStoresOnlyOneRow() throws Exception {
    Member member = member();
    Asset asset = asset();
    Portfolio portfolio = legacyHolding(member, asset, FIRST);
    price(asset, FIRST, 110);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return snapshotTransactions.createSnapshot(portfolio.getId(), FIRST, null);
              });
      var second =
          executor.submit(
              () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return snapshotTransactions.createSnapshot(portfolio.getId(), FIRST, null);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(points(portfolio)).hasSize(1);
  }

  @Test
  void snapshotWithoutClosingPricesIsPreservedAndDoesNotChangePreviousTradingDate() {
    Member member = member();
    Asset asset = asset();
    Portfolio portfolio = legacyHolding(member, asset, FIRST.minusDays(1));
    PortfolioDailySnapshot stale = new PortfolioDailySnapshot(portfolio, FIRST.minusDays(1));
    stale.update(
        new BigDecimal("200"), new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    snapshots.saveAndFlush(stale);
    price(asset, FIRST, 110);
    price(asset, SECOND, 120);

    snapshotService.createLatestSnapshots();

    assertThat(snapshots.findByPortfolioIdAndBaseDate(portfolio.getId(), FIRST.minusDays(1)))
        .isPresent();
    assertThat(points(portfolio)).hasSize(2);
    assertThat(points(portfolio).getFirst().getDailyProfitLoss()).isNull();
    assertThat(points(portfolio).getLast().getDailyProfitLoss()).isEqualByComparingTo("20");
  }

  @Test
  void unrelatedUsTradingDayDoesNotChangeDomesticSnapshotDatesOrPreviousPrice() {
    Member member = member();
    Asset domestic = asset();
    Portfolio portfolio = legacyHolding(member, domestic, FIRST);
    price(domestic, FIRST, 100);
    price(domestic, THIRD, 120);
    Asset us =
        assets.save(
            Asset.listedStock("US", UUID.randomUUID().toString(), StockMarket.NASDAQ, null, FIRST));
    price(us, SECOND, 999);

    snapshotService.createLatestSnapshots();

    assertThat(points(portfolio))
        .extracting(PortfolioDailySnapshot::getBaseDate)
        .containsExactly(FIRST, THIRD);
    assertThat(points(portfolio).getLast().getDailyProfitLoss()).isEqualByComparingTo("40");
    buy(member, domestic, FIRST, 1, 100);
    assertThat(points(portfolio))
        .extracting(PortfolioDailySnapshot::getBaseDate)
        .containsExactly(FIRST, THIRD);
    assertThat(points(portfolio).getLast().getDailyProfitLoss()).isEqualByComparingTo("60");
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private Asset asset() {
    String code = UUID.randomUUID().toString();
    return assets.save(
        Asset.listedStock(
            "스냅샷 종목",
            code,
            StockMarket.KOSPI,
            "KR" + code.replace("-", "").substring(0, 10),
            FIRST.minusDays(1)));
  }

  private Portfolio legacyHolding(Member member, Asset asset, LocalDate date) {
    Portfolio portfolio = portfolios.save(Portfolio.create(member));
    holdings.save(new PortfolioHolding(portfolio, asset, 2, new BigDecimal("100")));
    histories.save(
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.ADDED,
            2,
            new BigDecimal("100"),
            date,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    return portfolio;
  }

  private void price(Asset asset, LocalDate date, int close) {
    prices.saveAndFlush(new StockDailyPrice(asset, date, BigDecimal.valueOf(close)));
  }

  private void priceSynced() {
    events.publishEvent(new StockPriceSyncResult(THIRD, 1, 1, 0, 0));
  }

  private long buy(Member member, Asset asset, LocalDate date, int quantity, int unitPrice) {
    var body = new java.util.HashMap<String, Object>(transaction(date, quantity, unitPrice));
    body.put("assetId", asset.getId());
    body.put("type", "BUY");
    return client(member)
        .body(body)
        .post(PORTFOLIO + "/transactions")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("transactionId");
  }

  private Map<String, Object> transaction(LocalDate date, int quantity, int unitPrice) {
    return Map.of(
        "tradeDate",
        date.toString(),
        "quantity",
        quantity,
        "unitPrice",
        unitPrice,
        "fee",
        0,
        "tax",
        0);
  }

  private List<PortfolioDailySnapshot> points(Portfolio portfolio) {
    return snapshots.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
        portfolio.getId(), FIRST, THIRD);
  }

  private void assertPerformance(Member member, List<Float> values) {
    client(member)
        .queryParam("from", FIRST.toString())
        .queryParam("to", THIRD.toString())
        .get(PORTFOLIO + "/performance")
        .then()
        .statusCode(200)
        .body("points.totalEvaluationAmount", equalTo(values));
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
}
