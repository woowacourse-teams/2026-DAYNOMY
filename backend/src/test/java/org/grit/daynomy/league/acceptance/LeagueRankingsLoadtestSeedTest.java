package org.grit.daynomy.league.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

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
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.grit.daynomy.member.domain.Member;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
  private LocalDate week;
  private String sql;

  @BeforeEach
  void preparePricesAndCalendarInIsolatedDatabase() throws Exception {
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
      assertThat(statistics.getEntityStatistics(Member.class.getName()).getLoadCount())
          .isEqualTo(20);
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

  private void seed() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
