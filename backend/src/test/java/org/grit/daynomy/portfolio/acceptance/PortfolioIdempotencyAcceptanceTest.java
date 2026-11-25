package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.support.MemberLockStatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "league.auto-sync.enabled=false",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=org.grit.daynomy.portfolio.support.MemberLockStatementInspector"
    })
class PortfolioIdempotencyAcceptanceTest {
  private static final String V1 = "/api/users/me/portfolio/transactions";
  private static final String V2 = "/api/v2/users/me/portfolio/transactions";
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
  }

  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired AssetRepository assets;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @ParameterizedTest
  @CsvSource({V1 + ",true", V2 + ",true", V1 + ",false", V2 + ",false"})
  void countsActualMemberLockQueriesAndCompletedRetriesDoNotLock(String endpoint, boolean keyed) {
    Fixture f = fixture();
    String requestKey = keyed ? key() : null;
    long before = MemberLockStatementInspector.count();

    Response created = post(f, endpoint, requestKey, body(f, 1));

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(MemberLockStatementInspector.count() - before).isEqualTo(keyed ? 2 : 1);
    if (keyed) {
      long afterCreate = MemberLockStatementInspector.count();
      Response replayed = post(f, endpoint, requestKey, body(f, 1));
      assertThat(replayed.statusCode()).isEqualTo(201);
      assertThat(replayed.asString()).isEqualTo(created.asString());
      assertThat(MemberLockStatementInspector.count()).isEqualTo(afterCreate);
    }
    assertState(f, 1, 1, keyed ? 1 : 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void retryReturnsOriginalResultEvenAfterAnotherTrade(String endpoint) {
    Fixture f = fixture();
    String key = key();
    Response original = post(f, endpoint, key, body(f, 1));
    assertThat(original.statusCode()).isEqualTo(201);
    assertThat(post(f, endpoint, key(), body(f, 2)).statusCode()).isEqualTo(201);
    Response retry = post(f, endpoint, key, body(f, 1));
    assertThat(retry.statusCode()).isEqualTo(201);
    assertThat(retry.asString()).isEqualTo(original.asString());
    assertState(f, 2, 3, 2);
  }

  @Test
  void v1AndV2ConcurrentRetriesShareOneCommittedResult() throws Exception {
    Fixture f = fixture();
    String key = key();
    var requests = new ArrayList<RequestSpecification>();
    for (int i = 0; i < 4; i++) requests.add(client(f, key).body(body(f, 1)));
    CountDownLatch start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<java.util.concurrent.Future<Response>>();
      for (int i = 0; i < 4; i++) {
        var request = requests.get(i);
        String endpoint = i % 2 == 0 ? V1 : V2;
        futures.add(
            pool.submit(
                () -> {
                  assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                  return request.post(endpoint);
                }));
      }
      start.countDown();
      var responses = new ArrayList<Response>();
      for (var future : futures) responses.add(future.get(15, TimeUnit.SECONDS));
      assertThat(responses).extracting(Response::statusCode).containsOnly(201);
      assertThat(responses.stream().map(Response::asString).distinct()).hasSize(1);
    }
    assertState(f, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void changedPayloadIsRejectedWithoutChangingTheLedger(String endpoint) {
    Fixture f = fixture();
    String key = key();
    assertThat(post(f, endpoint, key, body(f, 1)).statusCode()).isEqualTo(201);
    Response conflict = post(f, endpoint, key, body(f, 2));
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(conflict.jsonPath().getString("code")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    assertState(f, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void decimalScalesAreCanonicalButTradeTimeIsPartOfTheIntent(String endpoint) {
    Fixture f = fixture();
    String key = key();
    var first = body(f, 1);
    first.put("unitPrice", new BigDecimal("100.00"));
    first.put("tradeTime", "09:00");
    Response original = post(f, endpoint, key, first);
    var equivalent = new HashMap<>(first);
    equivalent.put("unitPrice", new BigDecimal("100.0000"));
    equivalent.put("fee", new BigDecimal("0.00"));
    assertThat(post(f, endpoint, key, equivalent).asString()).isEqualTo(original.asString());
    equivalent.put("tradeTime", "09:01");
    assertThat(post(f, endpoint, key, equivalent).statusCode()).isEqualTo(409);
    assertState(f, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void failedSellRollsBackHistoryAndDoesNotReserveTheKey(String endpoint) {
    Fixture f = fixture();
    String key = key();
    var sell = body(f, 1);
    sell.put("type", "SELL");
    assertThat(post(f, endpoint, key, sell).statusCode()).isEqualTo(400);
    assertState(f, 0, 0, 0);
    assertThat(post(f, endpoint, key, body(f, 1)).statusCode()).isEqualTo(201);
    assertState(f, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void failureStoringTheResultRollsBackTheTradeToo(String endpoint) {
    Fixture f = fixture();
    String key = key();
    jdbc.execute(
        """
        CREATE FUNCTION reject_lab_request() RETURNS trigger AS $$
        BEGIN
          IF NEW.request_key = '%s' THEN RAISE EXCEPTION 'Synthetic result storage failure'; END IF;
          RETURN NEW;
        END; $$ LANGUAGE plpgsql
        """
            .formatted(key));
    jdbc.execute(
        "CREATE TRIGGER reject_lab_request BEFORE INSERT ON portfolio_transaction_requests "
            + "FOR EACH ROW EXECUTE FUNCTION reject_lab_request()");
    try {
      assertThat(post(f, endpoint, key, body(f, 1)).statusCode()).isEqualTo(500);
      assertState(f, 0, 0, 0);
    } finally {
      jdbc.execute("DROP TRIGGER reject_lab_request ON portfolio_transaction_requests");
      jdbc.execute("DROP FUNCTION reject_lab_request()");
    }
    assertThat(post(f, endpoint, key, body(f, 1)).statusCode()).isEqualTo(201);
    assertState(f, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void lockTimeoutIsRetryableAndLeavesNoPartialTrade(String endpoint) throws Exception {
    Fixture f = fixture();
    String key = key();
    var request = client(f, key).body(body(f, 1));
    try (Connection lock = dataSource.getConnection()) {
      lock.setAutoCommit(false);
      try (var statement = lock.prepareStatement("SELECT id FROM members WHERE id=? FOR UPDATE")) {
        statement.setLong(1, f.member().getId());
        statement.executeQuery().close();
      }
      try (var pool = Executors.newSingleThreadExecutor()) {
        Response response = pool.submit(() -> request.post(endpoint)).get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.jsonPath().getString("code")).isEqualTo("PORTFOLIO_TRANSACTION_BUSY");
      }
      assertState(f, 0, 0, 0);
      lock.rollback();
    }
    assertThat(post(f, endpoint, key, body(f, 1)).statusCode()).isEqualTo(201);
    assertState(f, 1, 1, 1);
  }

  @Test
  void deletingTheTradeDoesNotAllowTheOriginalRequestToExecuteAgain() {
    Fixture f = fixture();
    String key = key();
    Response original = post(f, V2, key, body(f, 1));
    long transactionId = original.jsonPath().getLong("transactionId");
    assertThat(client(f, null).delete(V1 + "/" + transactionId).statusCode()).isEqualTo(200);
    assertThat(post(f, V1, key, body(f, 1)).asString()).isEqualTo(original.asString());
    assertState(f, 0, 0, 1);
  }

  @Test
  void sameKeyBelongsToTheAuthenticatedMemberAndDifferentKeysAllowIdenticalTrades() {
    Fixture a = fixture();
    Fixture b = fixture();
    String key = key();
    assertThat(post(a, V1, key, body(a, 1)).statusCode()).isEqualTo(201);
    assertThat(post(b, V2, key, body(b, 1)).statusCode()).isEqualTo(201);
    assertThat(post(a, V2, key(), body(a, 1)).statusCode()).isEqualTo(201);
    assertState(a, 2, 2, 2);
    assertState(b, 1, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {V1, V2})
  void keyIsOptionalForExistingClientsButInvalidPresentKeysAreRejected(String endpoint) {
    Fixture f = fixture();
    for (String invalid : List.of("bad key", "a".repeat(129), "")) {
      assertThat(post(f, endpoint, invalid, body(f, 1)).statusCode()).isEqualTo(400);
    }
    assertState(f, 0, 0, 0);
    assertThat(post(f, endpoint, null, body(f, 1)).statusCode()).isEqualTo(201);
    assertThat(post(f, endpoint, null, body(f, 1)).statusCode()).isEqualTo(201);
    assertState(f, 2, 2, 0);
  }

  private void assertState(Fixture f, long historyCount, long quantity, long requestCount) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM portfolio_holding_histories h JOIN portfolios p "
                    + "ON p.id=h.portfolio_id WHERE p.member_id=?",
                Long.class,
                f.member().getId()))
        .isEqualTo(historyCount);
    assertThat(
            jdbc.queryForObject(
                "SELECT coalesce(sum(h.quantity),0) FROM portfolio_holdings h JOIN portfolios p "
                    + "ON p.id=h.portfolio_id WHERE p.member_id=?",
                Long.class,
                f.member().getId()))
        .isEqualTo(quantity);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM portfolio_transaction_requests WHERE member_id=?",
                Long.class,
                f.member().getId()))
        .isEqualTo(requestCount);
  }

  private Fixture fixture() {
    String key = key();
    Member member =
        members.save(
            Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
    Asset asset =
        assets.save(
            Asset.listedStock(
                "Idempotency test",
                key,
                StockMarket.KOSPI,
                "KR" + key.replace("-", "").substring(0, 10),
                TODAY.minusDays(1)));
    return new Fixture(member, asset);
  }

  private Map<String, Object> body(Fixture f, int quantity) {
    return new HashMap<>(
        Map.of(
            "assetId",
            f.asset().getId(),
            "type",
            "BUY",
            "tradeDate",
            TODAY.toString(),
            "quantity",
            quantity,
            "unitPrice",
            100,
            "fee",
            0,
            "tax",
            0));
  }

  private Response post(Fixture f, String endpoint, String key, Map<String, Object> body) {
    return client(f, key).body(body).post(endpoint);
  }

  private RequestSpecification client(Fixture f, String key) {
    var csrf = given().port(port).get("/api/auth/csrf");
    var request =
        given()
            .port(port)
            .contentType("application/json")
            .cookies(csrf.cookies())
            .cookie(
                "access_token",
                tokens.createTokenPair(f.member().getId(), MemberRole.USER).accessToken())
            .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
    return key == null ? request : request.header("Idempotency-Key", key);
  }

  private static String key() {
    return UUID.randomUUID().toString();
  }

  private record Fixture(Member member, Asset asset) {}
}
