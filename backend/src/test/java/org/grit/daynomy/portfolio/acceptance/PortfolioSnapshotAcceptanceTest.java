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

  @Test
  void openingBalancesAreAtomicRejectDuplicatesAndDoNotCreateBuyTrades() {
    Member member = member();
    Asset first = asset();
    Asset second = asset();
    var row = Map.of("assetId", first.getId(), "quantity", 3, "averagePurchasePrice", 110);
    var another = Map.of("assetId", second.getId(), "quantity", 2, "averagePurchasePrice", 362500);
    client(member)
        .body(Map.of("holdings", List.of(row, row)))
        .post(PORTFOLIO + "/holdings/bulk")
        .then()
        .statusCode(400)
        .body("code", equalTo("DUPLICATE_PORTFOLIO_ASSET"));
    assertThat(portfolios.findByMemberId(member.getId())).isEmpty();
    client(member)
        .body(
            Map.of(
                "holdings",
                List.of(
                    row,
                    Map.of("assetId", Long.MAX_VALUE, "quantity", 1, "averagePurchasePrice", 100))))
        .post(PORTFOLIO + "/holdings/bulk")
        .then()
        .statusCode(404);
    assertThat(portfolios.findByMemberId(member.getId())).isEmpty();
    client(member)
        .body(Map.of("holdings", List.of(row, another)))
        .post(PORTFOLIO + "/holdings/bulk")
        .then()
        .statusCode(201)
        .body("holdings.quantity", equalTo(List.of(3, 2)))
        .body("totalRealizedProfitLoss", equalTo(0.0F));
    Long portfolioId = portfolios.findByMemberId(member.getId()).orElseThrow().getId();
    var history =
        histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId);
    assertThat(history)
        .hasSize(2)
        .allSatisfy(
            item -> {
              assertThat(item.getChangeType())
                  .isEqualTo(PortfolioHoldingChangeType.OPENING_BALANCE);
              assertThat(item.getTransactionQuantity()).isNull();
              assertThat(item.getUnitPrice()).isNull();
              assertThat(item.getEffectiveDate())
                  .isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
            });
    var third = asset();
    client(member)
        .body(
            Map.of(
                "holdings",
                List.of(
                    Map.of("assetId", third.getId(), "quantity", 1, "averagePurchasePrice", 100),
                    row)))
        .post(PORTFOLIO + "/holdings/bulk")
        .then()
        .statusCode(400);
    assertThat(holdings.findByPortfolioIdAndAssetId(portfolioId, third.getId())).isEmpty();
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(2);
  }

  @Test
  void nativeAverageSurvivesCreateReadAndBalanceAdjustment() {
    Member member = member();
    Asset usd =
        assets.save(
            Asset.listedStock(
                "달러 평단가 검증", UUID.randomUUID() + ".US", StockMarket.NASDAQ, null, FIRST));
    var row =
        Map.of(
            "assetId",
            usd.getId(),
            "quantity",
            2,
            "averagePurchasePrice",
            350000,
            "nativeAveragePurchasePrice",
            250.1234);
    client(member)
        .body(Map.of("holdings", List.of(row)))
        .post(PORTFOLIO + "/holdings/bulk")
        .then()
        .statusCode(201)
        .body("holdings[0].nativeAveragePurchasePrice", equalTo(250.1234F));
    client(member)
        .get(PORTFOLIO)
        .then()
        .statusCode(200)
        .body("holdings[0].nativeAveragePurchasePrice", equalTo(250.1234F));
    var changed =
        Map.of(
            "assetId",
            usd.getId(),
            "quantity",
            3,
            "averagePurchasePrice",
            308000,
            "nativeAveragePurchasePrice",
            220.0);
    client(member)
        .body(Map.of("holdings", List.of(changed)))
        .patch(PORTFOLIO + "/holdings")
        .then()
        .statusCode(200)
        .body("[0].nativeAveragePurchasePrice", equalTo(220.0F));
    client(member)
        .get(PORTFOLIO)
        .then()
        .statusCode(200)
        .body("holdings[0].nativeAveragePurchasePrice", equalTo(220.0F));
    var portfolioId = portfolios.findByMemberId(member.getId()).orElseThrow().getId();
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .extracting(PortfolioHoldingHistory::getNativeAveragePurchasePrice)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("250.1234"), new BigDecimal("220"));
  }

  @Test
  void bulkHoldingsAreAtomicAndDoNotRecordTrades() {
    Member member = member();
    Asset first = asset();
    Asset second = asset();
    buy(member, first, FIRST, 2, 100);
    buy(member, second, FIRST, 2, 200);
    Long portfolioId = portfolios.findByMemberId(member.getId()).orElseThrow().getId();
    var changes =
        List.of(
            Map.of("assetId", first.getId(), "quantity", 3, "averagePurchasePrice", 110),
            Map.of("assetId", second.getId(), "quantity", 4, "averagePurchasePrice", 210));
    client(member)
        .body(Map.of("holdings", changes))
        .patch(PORTFOLIO + "/holdings")
        .then()
        .statusCode(200)
        .body("quantity", equalTo(List.of(3, 4)));
    var history =
        histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId);
    assertThat(history).hasSize(4);
    assertThat(
            history.stream()
                .filter(item -> item.getChangeType() == PortfolioHoldingChangeType.ADJUSTED)
                .count())
        .isEqualTo(2);
    client(member)
        .body(Map.of("holdings", changes))
        .patch(PORTFOLIO + "/holdings")
        .then()
        .statusCode(200);
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(4);
    client(member)
        .body(
            Map.of(
                "holdings",
                List.of(
                    Map.of("assetId", first.getId(), "quantity", 9, "averagePurchasePrice", 110),
                    Map.of("assetId", Long.MAX_VALUE, "quantity", 4, "averagePurchasePrice", 210))))
        .patch(PORTFOLIO + "/holdings")
        .then()
        .statusCode(404);
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, first.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(3);
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(4);
    client(member)
        .body(Map.of("holdings", List.of(changes.getFirst(), changes.getFirst())))
        .patch(PORTFOLIO + "/holdings")
        .then()
        .statusCode(400);
  }

  @Test
  void csvImportOrdersTradesDeduplicatesAndRollsBackEveryRowOnFailure() {
    Member member = member();
    Asset asset = asset();
    var buy = new java.util.HashMap<String, Object>(transaction(FIRST, 3, 100));
    buy.put("assetId", asset.getId());
    buy.put("type", "BUY");
    var sell = new java.util.HashMap<String, Object>(transaction(SECOND, 1, 150));
    sell.put("assetId", asset.getId());
    sell.put("type", "SELL");
    var batch = Map.of("transactions", List.of(sell, buy));
    client(member)
        .body(batch)
        .post(PORTFOLIO + "/transactions/import")
        .then()
        .statusCode(200)
        .body("importedCount", equalTo(2))
        .body("portfolio.holdings[0].quantity", equalTo(2));
    client(member)
        .body(batch)
        .post(PORTFOLIO + "/transactions/import")
        .then()
        .statusCode(200)
        .body("importedCount", equalTo(0))
        .body("alreadyImported", equalTo(true));
    Long portfolioId = portfolios.findByMemberId(member.getId()).orElseThrow().getId();
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(2);
    sell.put("quantity", 99);
    client(member)
        .body(Map.of("transactions", List.of(buy, sell)))
        .post(PORTFOLIO + "/transactions/import")
        .then()
        .statusCode(400)
        .body("code", equalTo("INSUFFICIENT_PORTFOLIO_QUANTITY"));
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(2);
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(2);
    sell.put("quantity", 1);
    buy.put("quantity", 4);
    client(member)
        .body(Map.of("transactions", List.of(buy, sell)))
        .post(PORTFOLIO + "/transactions/import")
        .then()
        .statusCode(200)
        .body("importedCount", equalTo(2));
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, asset.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(5);
    // Another member can import the same trades into their own portfolio.
    client(member())
        .body(batch)
        .post(PORTFOLIO + "/transactions/import")
        .then()
        .statusCode(200)
        .body("importedCount", equalTo(2));
  }

  @Test
  void bulkTradeFailureAlsoRollsBackFirstPortfolioCreation() {
    Member member = member();
    Asset asset = asset();
    var buy = new java.util.HashMap<String, Object>(transaction(FIRST, 1, 100));
    buy.put("assetId", asset.getId());
    buy.put("type", "BUY");
    var sell = new java.util.HashMap<String, Object>(buy);
    sell.put("assetId", asset().getId());
    sell.put("type", "SELL");
    sell.put("quantity", 2);
    client(member)
        .body(Map.of("transactions", List.of(buy, sell)))
        .post(PORTFOLIO + "/transactions/bulk")
        .then()
        .statusCode(400);
    assertThat(portfolios.findByMemberId(member.getId())).isEmpty();
    sell.put("quantity", 1);
    sell.put("type", "BUY");
    client(member)
        .body(Map.of("transactions", List.of(buy, sell)))
        .post(PORTFOLIO + "/transactions/bulk")
        .then()
        .statusCode(200)
        .body("portfolio.holdings.size()", equalTo(2));
  }

  @Test
  void bulkAddRejectsOwnedAndRepeatedAssetsWithoutSavingAnyRows() {
    Member member = member();
    Asset existing = asset();
    Asset added = asset();
    buy(member, existing, FIRST, 2, 100);
    var newRow = new java.util.HashMap<String, Object>(transaction(SECOND, 1, 100));
    newRow.put("assetId", added.getId());
    newRow.put("type", "BUY");
    var ownedRow = new java.util.HashMap<String, Object>(newRow);
    ownedRow.put("assetId", existing.getId());
    for (var rows : List.of(List.of(newRow, ownedRow), List.of(newRow, newRow))) {
      client(member)
          .body(Map.of("transactions", rows))
          .post(PORTFOLIO + "/transactions/bulk")
          .then()
          .statusCode(400)
          .body("code", equalTo("DUPLICATE_PORTFOLIO_ASSET"));
    }
    Long portfolioId = portfolios.findByMemberId(member.getId()).orElseThrow().getId();
    assertThat(holdings.findByPortfolioIdAndAssetId(portfolioId, added.getId())).isEmpty();
    assertThat(
            holdings
                .findByPortfolioIdAndAssetId(portfolioId, existing.getId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(2);
    assertThat(histories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId))
        .hasSize(1);
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
