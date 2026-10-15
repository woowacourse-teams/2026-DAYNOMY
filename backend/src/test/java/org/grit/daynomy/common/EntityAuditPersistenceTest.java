package org.grit.daynomy.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityAuditPersistenceTest {
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

  @Autowired private TestEntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void persistsAuditTimesForAllFiveEntitiesAndKeepsCreationTimeOnUpdate() {
    Member member =
        entityManager.persist(
            Member.createGoogleMember("audit-member", "audit@example.invalid", "감사 시각 테스트", null));
    Portfolio portfolio = entityManager.persist(Portfolio.create(member));
    InvestorProfile profile =
        entityManager.persist(
            InvestorProfile.create(
                member, "감사 시각 테스트", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED));
    PortfolioAnalysis analysis =
        entityManager.persist(
            PortfolioAnalysis.create(
                portfolio,
                null,
                "audit-test",
                "test-model",
                new PortfolioAnalysisSnapshot(
                    List.of(), PortfolioImpactSummary.calculate(List.of()), "테스트 분석", List.of()),
                Instant.parse("2026-10-12T07:00:00Z"),
                "최초 분석"));
    var check = entityManager.persist(new PortfolioAnalysisCheck(portfolio));
    var participation =
        entityManager.persist(
            new LeagueParticipation(profile, Instant.parse("2026-10-12T00:00:00Z")));
    var leaderboard =
        entityManager.persist(
            new WeeklyLeaderboard(
                LocalDate.of(2026, 10, 12),
                LeagueType.WEEKLY_RETURN,
                1,
                LocalDate.of(2026, 10, 16),
                "INITIAL_FINALIZATION",
                List.of()));
    List<BaseEntity> entities =
        List.of(
            entityManager.persist(new MarketTradingDay(LocalDate.of(2031, 1, 2), true)),
            participation,
            leaderboard,
            check,
            entityManager.persist(
                new PortfolioAnalysisEvidence(
                    analysis,
                    "테스트 종목",
                    "실적 발표",
                    "https://example.invalid/earnings",
                    LocalDate.of(2026, 10, 12),
                    null,
                    "테스트 근거",
                    null)));
    entityManager.flush();
    entityManager.clear();

    for (BaseEntity entity : entities) {
      BaseEntity restored = entityManager.find(entity.getClass(), entityManager.getId(entity));
      assertThat(entity.getCreatedAt()).isNotNull();
      assertThat(restored.getCreatedAt())
          .isEqualTo(entity.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
      assertThat(restored.getUpdatedAt()).isNotNull();
    }

    jdbc.update(
        "UPDATE portfolio_analysis_checks SET updated_at = ? WHERE portfolio_id = ?",
        Timestamp.from(Instant.EPOCH),
        portfolio.getId());
    entityManager.clear();
    var restored = entityManager.find(PortfolioAnalysisCheck.class, portfolio.getId());
    Instant createdAt = restored.getCreatedAt();
    restored.recordSuccess(Instant.parse("2026-10-12T08:00:00Z"));
    entityManager.flush();
    entityManager.clear();

    restored = entityManager.find(PortfolioAnalysisCheck.class, portfolio.getId());
    assertThat(restored.getCreatedAt()).isEqualTo(createdAt);
    assertThat(restored.getUpdatedAt()).isAfter(Instant.EPOCH);
    assertThat(restored.getLastSucceededAt()).isEqualTo(Instant.parse("2026-10-12T08:00:00Z"));
  }

  @Test
  void upgradesExistingRowsWithoutChangingBusinessTimes() throws Exception {
    String schema = "audit_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    configuration.target("44").load().migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      connection.setSchema(schema);
      sql.execute(
          """
          UPDATE market_trading_days SET collected_at = TIMESTAMPTZ '2026-10-13 08:00:00Z',
              last_attempted_at = TIMESTAMPTZ '2026-10-13 07:30:00Z'
          WHERE base_date = DATE '2026-10-13';
          INSERT INTO weekly_leaderboards
              (week_start, league_type, calculation_version, as_of_date, finalized_at, change_reason, entries)
          VALUES (DATE '2026-10-05', 'WEEKLY_RETURN', 1, DATE '2026-10-08',
              TIMESTAMPTZ '2026-10-08 08:00:00Z', 'INITIAL_FINALIZATION', '[]'::jsonb);
          """);
      configuration.target("latest").load().migrate();

      try (var result =
          sql.executeQuery(
              "SELECT collected_at, last_attempted_at, created_at, updated_at FROM market_trading_days WHERE base_date = DATE '2026-10-13'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getTimestamp("collected_at").toInstant())
            .isEqualTo(Instant.parse("2026-10-13T08:00:00Z"));
        assertThat(result.getTimestamp("last_attempted_at").toInstant())
            .isEqualTo(Instant.parse("2026-10-13T07:30:00Z"));
        assertThat(result.getTimestamp("created_at")).isNotNull();
        assertThat(result.getTimestamp("updated_at")).isEqualTo(result.getTimestamp("created_at"));
      }
      try (var result =
          sql.executeQuery(
              "SELECT finalized_at, entries::text, created_at, updated_at FROM weekly_leaderboards")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getTimestamp("finalized_at").toInstant())
            .isEqualTo(Instant.parse("2026-10-08T08:00:00Z"));
        assertThat(result.getString("entries")).isEqualTo("[]");
        assertThat(result.getTimestamp("created_at")).isNotNull();
        assertThat(result.getTimestamp("updated_at")).isEqualTo(result.getTimestamp("created_at"));
      }
      try (var result =
          sql.executeQuery(
              """
              SELECT COUNT(*) FROM information_schema.columns
              WHERE table_schema = current_schema()
                AND table_name IN ('market_trading_days', 'league_participations', 'weekly_leaderboards',
                    'portfolio_analysis_checks', 'portfolio_analysis_evidence')
                AND column_name IN ('created_at', 'updated_at') AND is_nullable = 'NO'
              """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(10);
      }
    }
  }
}
