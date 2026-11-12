package org.grit.daynomy.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatabaseIsolationTest extends SharedDatabaseTest {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  @Test
  void cleanupRemovesCommittedRowsAndRestoresCalendarWithoutRerunningMigrations() throws Exception {
    var calendar = jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date");
    var industries = jdbc.queryForList("SELECT * FROM industries ORDER BY id");
    long maxIndustryId =
        industries.stream()
            .mapToLong(row -> ((Number) row.get("id")).longValue())
            .max()
            .orElseThrow();
    var migrations =
        jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    insertMember();
    jdbc.update("DELETE FROM market_trading_days");
    jdbc.update("DELETE FROM industries");

    DatabaseCleaner.reset(dataSource);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM market_trading_days ORDER BY base_date"))
        .isEqualTo(calendar);
    assertThat(jdbc.queryForList("SELECT * FROM industries ORDER BY id")).isEqualTo(industries);
    assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"))
        .isEqualTo(migrations);
    assertThat(insertMember()).isEqualTo(1L);
    assertThat(insertIndustry()).isGreaterThan(maxIndustryId);
  }

  @Test
  void eachTestStartsWithoutPreviouslyCommittedMembers() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isZero();
    insertMember();
  }

  private long insertMember() {
    return jdbc.queryForObject(
        """
        INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
        VALUES ('GOOGLE', 'isolation', 'isolation@example.invalid', 'isolation', 'USER', 'ACTIVE', now(), now())
        RETURNING id
        """,
        Long.class);
  }

  private long insertIndustry() {
    return jdbc.queryForObject(
        """
        INSERT INTO industries(code, name, display_order, active, created_at, updated_at)
        VALUES ('ISOLATION', '격리 테스트', 99, true, now(), now())
        RETURNING id
        """,
        Long.class);
  }
}
