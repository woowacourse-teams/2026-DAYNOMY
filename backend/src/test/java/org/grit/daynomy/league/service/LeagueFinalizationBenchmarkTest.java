package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "LEAGUE_BENCHMARK", matches = "true")
@ActiveProfiles("test")
@SpringBootTest
class LeagueFinalizationBenchmarkTest {
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

  @Autowired private JdbcTemplate jdbc;
  @Autowired private LeagueService leagueService;

  @Test
  void measureFinalizationWithProductionSizedHistory() throws Exception {
    int participants =
        Integer.parseInt(System.getenv().getOrDefault("LEAGUE_BENCHMARK_PROFILES", "1000"));
    int years = Integer.parseInt(System.getenv().getOrDefault("LEAGUE_BENCHMARK_YEARS", "3"));
    LocalDate currentWeek =
        LocalDate.now(ZoneId.of("Asia/Seoul"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate week = currentWeek.minusWeeks(1);
    LocalDate start =
        week.minusYears(years).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate enrolled = start.minusWeeks(1);
    LocalDate end = week.plusDays(4);

    leagueService.finalizeWeeks();
    jdbc.update(
        """
        INSERT INTO market_trading_days(base_date, trading, collected_at)
        SELECT d::date, EXTRACT(ISODOW FROM d) < 6,
          CASE WHEN EXTRACT(ISODOW FROM d) < 6 AND d::date <= ?::date
            THEN (d::date + TIME '17:00') AT TIME ZONE 'Asia/Seoul' ELSE NULL END
        FROM generate_series(?::date, ?::date, INTERVAL '1 day') d
        ON CONFLICT (base_date) DO UPDATE
          SET trading = EXCLUDED.trading, collected_at = EXCLUDED.collected_at
        """,
        Date.valueOf(end),
        Date.valueOf(enrolled),
        Date.valueOf(currentWeek.plusDays(6)));
    jdbc.update(
        """
        INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
        SELECT 'GOOGLE', 'league-bench-' || n, 'league-bench-' || n || '@example.invalid',
          'bench' || n, 'USER', 'ACTIVE', now(), now()
        FROM generate_series(1, ?) n
        """,
        participants);
    jdbc.update(
        """
        INSERT INTO public_investor_profiles(member_id, public_id, display_name, bio,
          experience_level, risk_profile, profile_public, league_enabled, allocation_public,
          premium_detail_enabled, league_enabled_at, tracking_started_on, created_at, updated_at)
        SELECT m.id, lpad(m.id::text, 36, '0'), 'bench' || m.id, '', 'BEGINNER', 'BALANCED',
          true, true, false, false, ?, ?, now(), now()
        FROM members m WHERE m.provider_id LIKE 'league-bench-%'
        """,
        Timestamp.from(enrolled.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()),
        Date.valueOf(start));
    jdbc.update(
        """
        INSERT INTO league_participations(profile_id, started_at)
        SELECT id, league_enabled_at FROM public_investor_profiles
        """);
    jdbc.update(
        """
        INSERT INTO shared_portfolios(member_id, created_at, updated_at)
        SELECT id, now(), now() FROM members WHERE provider_id LIKE 'league-bench-%'
        """);
    jdbc.update(
        """
        INSERT INTO portfolio_daily_returns(portfolio_id, base_date, daily_return_rate,
          starting_evaluation_amount, ending_evaluation_amount, max_holding_weight,
          current_holding_weight, eligible, tracking_baseline, calculation_version,
          calculated_at, created_at, updated_at)
        SELECT p.id, d.base_date, 0.1, 100, 100.1, 50, 50, true, false, 1, now(), now(), now()
        FROM shared_portfolios p CROSS JOIN market_trading_days d
        WHERE d.trading AND d.base_date BETWEEN ? AND ?
        """,
        Date.valueOf(start),
        Date.valueOf(end));

    long dailyRows =
        jdbc.queryForObject("SELECT count(*) FROM portfolio_daily_returns", Long.class);
    long started = System.nanoTime();
    leagueService.finalizeWeeks(week);
    long firstMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    started = System.nanoTime();
    leagueService.finalizeWeeks(week);
    long steadyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    long leaderboards = jdbc.queryForObject("SELECT count(*) FROM weekly_leaderboards", Long.class);
    assertThat(leaderboards).isEqualTo(3);
    Path report = Path.of("build/reports/league-finalization-benchmark.txt");
    Files.createDirectories(report.getParent());
    Files.writeString(
        report,
        "profiles="
            + participants
            + " years="
            + years
            + " daily_rows="
            + dailyRows
            + " first_ms="
            + firstMs
            + " steady_ms="
            + steadyMs
            + " leaderboards="
            + leaderboards
            + "\n");
  }
}
