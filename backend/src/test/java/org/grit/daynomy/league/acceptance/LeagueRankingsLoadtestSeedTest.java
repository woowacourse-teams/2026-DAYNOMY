package org.grit.daynomy.league.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.specification.RequestSpecification;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.service.LeagueRankingCache;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.stat=OFF",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
class LeagueRankingsLoadtestSeedTest {
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
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private LeagueReturnService leagueReturns;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private LeagueRankingCache rankingCache;
  private LocalDate week;
  private String sql;

  @BeforeEach
  void preparePricesAndCalendarInIsolatedDatabase() throws Exception {
    rankingCache.invalidateAfterCommit();
    week =
        LocalDate.now(ZoneId.of("Asia/Seoul"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1);
    jdbc.execute("TRUNCATE members, assets, weekly_leaderboards CASCADE");
    jdbc.update("UPDATE market_trading_days SET collected_at = NULL");
    jdbc.update(
        """
        INSERT INTO market_trading_days(base_date, trading, collected_at)
        SELECT d::date, EXTRACT(ISODOW FROM d) < 6,
          CASE WHEN EXTRACT(ISODOW FROM d) < 6
            THEN (d::date + TIME '17:00') AT TIME ZONE 'Asia/Seoul' END
        FROM generate_series(?::date, ?::date, INTERVAL '1 day') d
        ON CONFLICT (base_date) DO UPDATE
          SET trading = EXCLUDED.trading, collected_at = EXCLUDED.collected_at
        """,
        week.toString(),
        week.plusDays(6).toString());
    jdbc.update(
        """
        INSERT INTO assets(name, category, asset_code, market, listed, created_at, updated_at)
        SELECT 'Seed stock ' || n, 'STOCK', 'SEED601-' || n, 'KOSPI', true, now(), now()
        FROM generate_series(1, 5) n
        """);
    jdbc.update(
        """
        INSERT INTO stock_daily_prices(asset_id, base_date, close_price, created_at, updated_at)
        SELECT a.id, d.base_date,
          10000 + a.id * 100 + (d.base_date - ?::date) * (a.id % 5 - 2) * 100, now(), now()
        FROM assets a CROSS JOIN market_trading_days d
        WHERE d.trading AND d.base_date BETWEEN ?::date AND ?::date
        """,
        week.toString(), week.toString(), week.plusDays(6).toString());
    jdbc.update(
        """
        INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
        VALUES ('GOOGLE', 'existing-user', 'existing@example.invalid', 'existing', 'USER', 'ACTIVE', now(), now())
        """);
    sql =
        Files.readString(Path.of("loadtest/sql/seed-league-rankings.sql"))
            .replace(
                "-- UPDATE lt601_config SET week_start = DATE '2026-10-19';",
                "UPDATE lt601_config SET week_start = DATE '" + week + "';");
  }

  @Test
  void seedsOneHundredRankedInvestorsWithoutChangingExistingDataOrDuplicatingOnRerun()
      throws Exception {
    sql = sql.replace("1000 AS target_members", "100 AS target_members");
    var calendar = jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date");
    var assets = jdbc.queryForList("SELECT * FROM assets ORDER BY id");
    var prices = jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id");
    var existing = jdbc.queryForList("SELECT * FROM members WHERE provider_id = 'existing-user'");

    seed();

    assertThat(count("members")).isEqualTo(101);
    assertThat(count("public_investor_profiles")).isEqualTo(100);
    assertThat(count("league_participations")).isEqualTo(100);
    assertThat(count("shared_portfolios")).isEqualTo(100);
    assertThat(count("shared_holdings")).isEqualTo(300);
    assertThat(count("shared_holding_histories")).isEqualTo(300);
    assertThat(count("portfolio_transactions")).isEqualTo(1000);
    assertThat(count("investment_reviews")).isBetween(1, 999);
    assertThat(count("portfolio_daily_returns")).isEqualTo(500);
    var publicIds = new HashSet<String>();
    var reviewedIds =
        new HashSet<>(
            jdbc.queryForList("SELECT transaction_id FROM investment_reviews", Long.class));
    Map<String, Integer> reviewedByInvestor = new HashMap<>();
    jdbc.query(
        """
        SELECT profile.public_id, decision.id
        FROM portfolio_transactions decision
        JOIN shared_portfolios portfolio ON portfolio.id = decision.portfolio_id
        JOIN public_investor_profiles profile ON profile.member_id = portfolio.member_id
        """,
        row -> {
          reviewedByInvestor.merge(
              row.getString(1), reviewedIds.contains(row.getLong(2)) ? 1 : 0, Integer::sum);
        });
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    for (int page = 0; page < 5; page++) {
      statistics.clear();
      var response =
          given()
              .port(port)
              .queryParam("weekStart", week.toString())
              .queryParam("leagueType", "WEEKLY_RETURN")
              .queryParam("page", page)
              .queryParam("size", 20)
              .get("/api/league/rankings")
              .then()
              .statusCode(200)
              .body("confirmed", equalTo(false))
              .body("totalCount", equalTo(100))
              .body("rankings", hasSize(20))
              .body("rankings.decisionCount", everyItem(equalTo(10)))
              .extract()
              .response();
      assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 12L);
      assertThat(statistics.getEntityStatistics(InvestmentDecision.class.getName()).getLoadCount())
          .isZero();
      assertThat(
              statistics.getEntityStatistics(PortfolioDailyReturn.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(SharedPortfolio.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(InvestorProfile.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(LeagueParticipation.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(Member.class.getName()).getLoadCount()).isZero();
      for (Map<String, Object> entry :
          response.jsonPath().<Map<String, Object>>getList("rankings")) {
        assertThat(new BigDecimal(entry.get("reviewCompletionRate").toString()))
            .isEqualByComparingTo(
                BigDecimal.valueOf(reviewedByInvestor.get(entry.get("publicId")) * 10L));
      }
      publicIds.addAll(response.jsonPath().getList("rankings.publicId", String.class));
    }
    assertThat(publicIds).hasSize(100);

    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("이미 있습니다");
    assertThat(count("members")).isEqualTo(101);
    assertThat(jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date"))
        .isEqualTo(calendar);
    assertThat(jdbc.queryForList("SELECT * FROM assets ORDER BY id")).isEqualTo(assets);
    assertThat(jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id")).isEqualTo(prices);
    assertThat(jdbc.queryForList("SELECT * FROM members WHERE provider_id = 'existing-user'"))
        .isEqualTo(existing);

    leagueReturns.recalculate();
    assertThat(
            jdbc.queryForObject(
                "SELECT max(calculation_version) FROM portfolio_daily_returns WHERE base_date BETWEEN ?::date AND ?::date",
                Integer.class,
                week.toString(),
                week.plusDays(6).toString()))
        .isEqualTo(1);
  }

  @Test
  void expandsExistingOneHundredInvestorsToOneThousandWithoutChangingTheirData() throws Exception {
    String expansionSql = sql;
    sql = sql.replace("1000 AS target_members", "100 AS target_members");
    seed();
    var existingMembers = jdbc.queryForList("SELECT * FROM members ORDER BY id");
    var existingProfiles = jdbc.queryForList("SELECT * FROM public_investor_profiles ORDER BY id");
    var existingHoldings = jdbc.queryForList("SELECT * FROM shared_holdings ORDER BY id");
    var existingDecisions = jdbc.queryForList("SELECT * FROM portfolio_transactions ORDER BY id");
    var existingReviews = jdbc.queryForList("SELECT * FROM investment_reviews ORDER BY id");
    var existingReturns = jdbc.queryForList("SELECT * FROM portfolio_daily_returns ORDER BY id");
    var calendar = jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date");
    var assets = jdbc.queryForList("SELECT * FROM assets ORDER BY id");
    var prices = jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id");
    sql = expansionSql;

    seed();

    assertThat(count("members")).isEqualTo(1001);
    assertThat(count("public_investor_profiles")).isEqualTo(1000);
    assertThat(count("league_participations")).isEqualTo(1000);
    assertThat(count("shared_portfolios")).isEqualTo(1000);
    assertThat(count("shared_holdings")).isEqualTo(3000);
    assertThat(count("shared_holding_histories")).isEqualTo(3000);
    assertThat(count("portfolio_transactions")).isEqualTo(10000);
    assertThat(count("portfolio_daily_returns")).isEqualTo(5000);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM members WHERE provider_id = 'lt601-rank-1000'",
                Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForList("SELECT * FROM members ORDER BY id LIMIT 101"))
        .isEqualTo(existingMembers);
    assertThat(jdbc.queryForList("SELECT * FROM public_investor_profiles ORDER BY id LIMIT 100"))
        .isEqualTo(existingProfiles);
    assertThat(jdbc.queryForList("SELECT * FROM shared_holdings ORDER BY id LIMIT 300"))
        .isEqualTo(existingHoldings);
    assertThat(jdbc.queryForList("SELECT * FROM portfolio_transactions ORDER BY id LIMIT 1000"))
        .isEqualTo(existingDecisions);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM investment_reviews ORDER BY id LIMIT " + existingReviews.size()))
        .isEqualTo(existingReviews);
    assertThat(jdbc.queryForList("SELECT * FROM portfolio_daily_returns ORDER BY id LIMIT 500"))
        .isEqualTo(existingReturns);
    assertThat(jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date"))
        .isEqualTo(calendar);
    assertThat(jdbc.queryForList("SELECT * FROM assets ORDER BY id")).isEqualTo(assets);
    assertThat(jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id")).isEqualTo(prices);

    var publicIds = new HashSet<String>();
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    for (int page = 0; page < 10; page++) {
      statistics.clear();
      var response =
          given()
              .port(port)
              .queryParam("weekStart", week.toString())
              .queryParam("leagueType", "WEEKLY_RETURN")
              .queryParam("page", page)
              .queryParam("size", 100)
              .get("/api/league/rankings")
              .then()
              .statusCode(200)
              .body("totalCount", equalTo(1000))
              .body("rankings", hasSize(100))
              .body("rankings.decisionCount", everyItem(equalTo(10)))
              .extract()
              .response();
      assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 12L);
      assertThat(
              statistics.getEntityStatistics(PortfolioDailyReturn.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(SharedPortfolio.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(InvestorProfile.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(LeagueParticipation.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getEntityStatistics(Member.class.getName()).getLoadCount()).isZero();
      publicIds.addAll(response.jsonPath().getList("rankings.publicId", String.class));
    }
    assertThat(publicIds).hasSize(1000);
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("이미 있습니다");
    assertThat(count("members")).isEqualTo(1001);
  }

  @Test
  void refusesIncompleteCollectionDuringExpansionWithoutChangingExistingInvestors()
      throws Exception {
    String expansionSql = sql;
    sql = sql.replace("1000 AS target_members", "100 AS target_members");
    seed();
    jdbc.update(
        "UPDATE market_trading_days SET collected_at = NULL WHERE base_date = ?::date",
        week.plusDays(2).toString());
    var existingMembers = jdbc.queryForList("SELECT * FROM members ORDER BY id");
    sql = expansionSql;

    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("불완전");

    assertThat(jdbc.queryForList("SELECT * FROM members ORDER BY id")).isEqualTo(existingMembers);
    assertThat(count("public_investor_profiles")).isEqualTo(100);
    assertThat(count("portfolio_transactions")).isEqualTo(1000);
    assertThat(count("portfolio_daily_returns")).isEqualTo(500);
  }

  @Test
  void refusesMissingPricesBeforeAddingAnyMember() {
    jdbc.update(
        "DELETE FROM stock_daily_prices WHERE base_date = ?::date", week.plusDays(2).toString());
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("3개 미만");
    assertThat(count("members")).isEqualTo(1);
    assertThat(count("public_investor_profiles")).isZero();
  }

  @Test
  void refusesIncompleteCollectionBeforeAddingAnyMember() {
    jdbc.update(
        "UPDATE market_trading_days SET collected_at = NULL WHERE base_date = ?::date",
        week.plusDays(2).toString());
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("불완전");
    assertThat(count("members")).isEqualTo(1);
    assertThat(count("shared_portfolios")).isZero();
  }

  @Test
  void seedsTenThousandParticipantsAndDataForAllTwentyUserApis() throws Exception {
    prepareUserApiSeed();
    jdbc.update(
        """
        INSERT INTO assets(name, category, asset_code, market, listed, created_at, updated_at)
        SELECT 'API seed stock ' || n, 'STOCK', 'SEED10K-' || n, 'KOSPI', true, now(), now()
        FROM generate_series(1, 25) n
        """);
    jdbc.update(
        """
        INSERT INTO stock_daily_prices(asset_id, base_date, close_price, created_at, updated_at)
        SELECT a.id, d.base_date, 10000 + a.id * 100 + (d.base_date - ?::date) * 10, now(), now()
        FROM assets a CROSS JOIN market_trading_days d
        WHERE a.asset_code LIKE 'SEED10K-%' AND d.trading AND d.base_date BETWEEN ?::date AND ?::date
        """,
        week.toString(), week.toString(), week.plusDays(6).toString());
    jdbc.update(
        """
        INSERT INTO shared_portfolios(member_id, created_at, updated_at)
        SELECT id, now(), now() FROM members WHERE provider_id='existing-user'
        """);
    jdbc.update(
        """
        INSERT INTO portfolio_transactions(portfolio_id, asset_id, request_key, transaction_type,
            quantity, unit_price, traded_on, decision_reason, expected_change,
            invalidation_condition, status, created_at, updated_at)
        SELECT p.id, (SELECT min(id) FROM assets), k.request_key, 'HOLD',
            1, 100, ?::date, '기존 투자 판단', '', '', 'ACTIVE', now(), now()
        FROM shared_portfolios p CROSS JOIN
            (VALUES ('legacy-request'), ('legacy:'), ('request:not-a-number'), ('lg10k:2')) k(request_key)
        """,
        week.toString());
    jdbc.update(
        """
        INSERT INTO investment_reviews(transaction_id, actual_result, difference_from_expectation,
            next_action, created_at, updated_at)
        SELECT min(id), '기존 결과', '기존 비교', '기존 계획', now(), now() FROM portfolio_transactions
        """);
    var existingDecisions = jdbc.queryForList("SELECT * FROM portfolio_transactions ORDER BY id");
    var existingReviews = jdbc.queryForList("SELECT * FROM investment_reviews ORDER BY id");
    var existing = jdbc.queryForList("SELECT * FROM members ORDER BY id");
    var calendar = jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date");
    var assets = jdbc.queryForList("SELECT * FROM assets ORDER BY id");
    var prices = jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id");

    seed();

    assertThat(count("members")).isEqualTo(10001);
    assertThat(count("public_investor_profiles")).isEqualTo(10000);
    assertThat(count("league_participations")).isEqualTo(10000);
    assertThat(count("shared_holdings")).isEqualTo(57500);
    assertThat(count("shared_holding_histories")).isEqualTo(57500);
    assertThat(count("portfolio_transactions")).isEqualTo(290004);
    assertThat(count("investment_reviews")).isEqualTo(145001);
    assertThat(jdbc.queryForList("SELECT * FROM portfolio_transactions ORDER BY id LIMIT 4"))
        .isEqualTo(existingDecisions);
    assertThat(jdbc.queryForList("SELECT * FROM investment_reviews ORDER BY id LIMIT 1"))
        .isEqualTo(existingReviews);
    assertThat(count("portfolio_daily_returns")).isEqualTo(50000);
    assertThat(count("portfolios")).isEqualTo(1000);
    assertThat(count("portfolio_holdings")).isEqualTo(7000);
    assertThat(count("portfolio_holding_histories")).isEqualTo(7000);
    assertThat(count("investor_follows")).isEqualTo(20000);
    assertThat(jdbc.queryForList("SELECT * FROM members ORDER BY id LIMIT 1")).isEqualTo(existing);
    assertThat(jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date"))
        .isEqualTo(calendar);
    assertThat(jdbc.queryForList("SELECT * FROM assets ORDER BY id")).isEqualTo(assets);
    assertThat(jdbc.queryForList("SELECT * FROM stock_daily_prices ORDER BY id")).isEqualTo(prices);
    given().port(port).get("/api/league/weeks").then().statusCode(200);
    given()
        .port(port)
        .queryParam("weekStart", week.toString())
        .get("/api/league/rankings")
        .then()
        .statusCode(200)
        .body("totalCount", equalTo(10000))
        .body("rankings", hasSize(20));
    String publicId =
        jdbc.queryForObject(
            "SELECT public_id FROM public_investor_profiles WHERE display_name='lt-lg10k-00001'",
            String.class);
    String publicPath = "/api/league/investors/" + publicId;
    given().port(port).get(publicPath).then().statusCode(200);
    given()
        .port(port)
        .get(publicPath + "/details")
        .then()
        .statusCode(200)
        .body("holdings", hasSize(5))
        .body("decisions", hasSize(20));
    given()
        .port(port)
        .queryParam("weekStart", week.toString())
        .get(publicPath + "/daily-history")
        .then()
        .statusCode(200)
        .body("days", hasSize(5));

    long memberId =
        jdbc.queryForObject(
            "SELECT id FROM members WHERE provider_id='lt-lg10k-00001'", Long.class);
    String mine = "/api/users/me";
    userApiClient(memberId)
        .get(mine + "/portfolio")
        .then()
        .statusCode(200)
        .body("holdings", hasSize(7));
    userApiClient(memberId).get(mine + "/investor-profile").then().statusCode(200);
    userApiClient(memberId)
        .body(
            Map.of(
                "displayName",
                "lt-lg10k-00001",
                "bio",
                "수정한 테스트 소개",
                "experienceLevel",
                "BEGINNER",
                "riskProfile",
                "BALANCED"))
        .put(mine + "/investor-profile")
        .then()
        .statusCode(200);
    userApiClient(memberId)
        .body(
            Map.of(
                "profilePublic",
                true,
                "leagueEnabled",
                true,
                "allocationPublic",
                false,
                "detailPublic",
                true))
        .put(mine + "/portfolio/publication")
        .then()
        .statusCode(200);
    userApiClient(memberId)
        .get(mine + "/portfolio/transactions")
        .then()
        .statusCode(200)
        .body("transactions", hasSize(20));
    long assetId =
        jdbc.queryForObject(
            "SELECT h.asset_id FROM shared_holdings h JOIN shared_portfolios p ON p.id=h.portfolio_id WHERE p.member_id=? ORDER BY h.id LIMIT 1",
            Long.class,
            memberId);
    long decisionId =
        ((Number)
                userApiClient(memberId)
                    .body(
                        Map.of(
                            "requestKey",
                            "loadtest-new-decision",
                            "assetId",
                            assetId,
                            "decision",
                            Map.of("reason", "신규 테스트 판단")))
                    .post(mine + "/portfolio/decisions")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id"))
            .longValue();
    String decisionPath = mine + "/portfolio/decisions/" + decisionId;
    userApiClient(memberId)
        .body(Map.of("reason", "수정한 테스트 이유"))
        .put(decisionPath + "/reason")
        .then()
        .statusCode(200);
    var review =
        Map.of(
            "actualResult", "테스트 결과", "differenceFromExpectation", "예상 비교", "nextAction", "다음 확인");
    long reviewId =
        ((Number)
                userApiClient(memberId)
                    .body(review)
                    .post(decisionPath + "/reviews")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id"))
            .longValue();
    userApiClient(memberId)
        .body(review)
        .put(decisionPath + "/reviews/" + reviewId)
        .then()
        .statusCode(200);
    String followId =
        jdbc.queryForObject(
            "SELECT public_id FROM public_investor_profiles WHERE display_name='lt-lg10k-01025'",
            String.class);
    userApiClient(memberId).post(mine + "/league/follows/" + followId).then().statusCode(204);
    userApiClient(memberId).delete(mine + "/league/follows/" + followId).then().statusCode(204);
    userApiClient(memberId)
        .get(mine + "/league/follows/summary")
        .then()
        .statusCode(200)
        .body("investors", hasSize(20));
    String shared = mine + "/shared-portfolio";
    userApiClient(memberId).get(shared).then().statusCode(200).body("holdings", hasSize(5));
    long sourceAssetId =
        jdbc.queryForObject(
            "SELECT h.asset_id FROM portfolio_holdings h JOIN portfolios p ON p.id=h.portfolio_id WHERE p.member_id=? AND NOT EXISTS (SELECT FROM shared_holdings s JOIN shared_portfolios sp ON sp.id=s.portfolio_id WHERE sp.member_id=p.member_id AND s.asset_id=h.asset_id) ORDER BY h.id LIMIT 1",
            Long.class,
            memberId);
    userApiClient(memberId)
        .body(
            Map.of(
                "holdings",
                java.util.List.of(
                    Map.of(
                        "assetId",
                        sourceAssetId,
                        "quantity",
                        10,
                        "averagePurchasePrice",
                        10000,
                        "decision",
                        Map.of("reason", "추가한 테스트 종목"))),
                "overwriteExisting",
                false))
        .post(shared + "/import")
        .then()
        .statusCode(200)
        .body("holdings", hasSize(6));
    userApiClient(memberId)
        .body(Map.of("hidden", true))
        .patch(shared + "/holdings/" + sourceAssetId + "/visibility")
        .then()
        .statusCode(200);
    userApiClient(memberId)
        .delete(shared + "/holdings/" + sourceAssetId)
        .then()
        .statusCode(200)
        .body("holdings", hasSize(5));
    assertThat(count("portfolio_holdings")).isEqualTo(7000);
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("이미 있습니다");
    assertThat(count("members")).isEqualTo(10001);
  }

  @Test
  void refusesUserApiSeedOutsideDevelopmentDatabase() throws Exception {
    sql = Files.readString(Path.of("loadtest/sql/seed-league-user-apis.sql"));
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("개발 DB");
    assertThat(count("members")).isEqualTo(1);
  }

  @Test
  void refusesUserApiSeedWithInsufficientPricesBeforeAddingMembers() throws Exception {
    prepareUserApiSeed();
    assertThatThrownBy(this::seed).isInstanceOf(SQLException.class).hasMessageContaining("20개 미만");
    assertThat(count("members")).isEqualTo(1);
    assertThat(count("shared_portfolios")).isZero();
  }

  private void prepareUserApiSeed() throws Exception {
    sql =
        Files.readString(Path.of("loadtest/sql/seed-league-user-apis.sql"))
            .replace(
                "'daynomy_dev'::text AS expected_database", "'test'::text AS expected_database")
            .replace(
                "-- UPDATE ltlg_config SET week_start = DATE '2026-10-19';",
                "UPDATE ltlg_config SET week_start = DATE '" + week + "';");
  }

  private RequestSpecification userApiClient(long memberId) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie("access_token", tokens.createTokenPair(memberId, MemberRole.USER).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }

  private void seed() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
    rankingCache.invalidateAfterCommit();
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "LEAGUE_RANKINGS_BENCHMARK", matches = "true")
  void rankingsMeetLoadThresholdsWithTenThousandParticipants() throws Exception {
    prepareUserApiSeed();
    jdbc.update(
        """
        INSERT INTO assets(name, category, asset_code, market, listed, created_at, updated_at)
        SELECT 'Benchmark stock ' || n, 'STOCK', 'BENCH601-' || n, 'KOSPI', true, now(), now()
        FROM generate_series(1, 25) n
        """);
    jdbc.update(
        """
        INSERT INTO stock_daily_prices(asset_id, base_date, close_price, created_at, updated_at)
        SELECT a.id, d.base_date, 10000 + a.id * 100 + (d.base_date - ?::date) * 10, now(), now()
        FROM assets a CROSS JOIN market_trading_days d
        WHERE a.asset_code LIKE 'BENCH601-%' AND d.trading AND d.base_date BETWEEN ?::date AND ?::date
        """,
        week.toString(), week.toString(), week.plusDays(6).toString());
    seed();
    assertThat(count("public_investor_profiles")).isEqualTo(10000);
    jdbc.execute("ANALYZE");
    Path report = Path.of(System.getProperty("java.io.tmpdir"), "daynomy-rankings-benchmark");
    Files.createDirectories(report);
    Path script = report.resolve("league-rankings-load.js");
    // 본 측정 조건을 유지하고 사전 조회의 주차만 격리 DB의 테스트 주차로 고정한다.
    Files.writeString(
        script,
        Files.readString(Path.of("../loadtest/league-rankings-load.js"))
            .replace(
                "function request(phase, weekStart)",
                "function request(phase, weekStart = '" + week + "')"));
    Path output = report.resolve("result.txt");
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    boolean statisticsEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(false);
    try (var recording = new Recording(Configuration.getConfiguration("profile"))) {
      recording.start();
      Process process =
          new ProcessBuilder(
                  "k6",
                  "run",
                  "--summary-export",
                  report.resolve("metrics.json").toString(),
                  "-e",
                  "BASE_URL=http://127.0.0.1:" + port,
                  "-e",
                  "RPS=100",
                  "-e",
                  "VUS=50",
                  script.toString())
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      try {
        assertThat(process.waitFor(7, TimeUnit.MINUTES)).as("k6 completion: %s", output).isTrue();
        assertThat(process.exitValue()).as(Files.readString(output)).isZero();
      } finally {
        if (process.isAlive()) process.destroyForcibly();
        recording.stop();
        recording.dump(report.resolve("recording.jfr"));
      }
    } finally {
      statistics.setStatisticsEnabled(statisticsEnabled);
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
