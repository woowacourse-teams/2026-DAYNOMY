package org.grit.daynomy.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;

class SharedSessionMigrationTest extends SharedDatabaseTest {
  @Test
  void upgradesExistingTradeMigrationWithoutChangingItsHistoryOrTable() throws Exception {
    String schema = "session_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    try (var connection =
            DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      configuration.target("98").load().migrate();
      connection.setSchema(schema);
      long tradeTable;
      try (var row = sql.executeQuery("SELECT 'portfolio_transaction_requests'::regclass::oid")) {
        assertThat(row.next()).isTrue();
        tradeTable = row.getLong(1);
      }
      configuration.target("latest").load().migrate();
      configuration.load().validate();
      try (var row =
          sql.executeQuery(
              "SELECT script, checksum FROM flyway_schema_history WHERE version = '98'")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString(1)).isEqualTo("V98__create_portfolio_transaction_requests.sql");
        assertThat(row.getInt(2)).isEqualTo(-569021117);
      }
      try (var row = sql.executeQuery("SELECT 'portfolio_transaction_requests'::regclass::oid")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getLong(1)).isEqualTo(tradeTable);
      }
      try (var row =
          sql.executeQuery(
              "SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                  + schema
                  + "' AND table_name IN ('spring_session', 'spring_session_attributes', 'guest_portfolio_analysis_trials')")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getInt(1)).isEqualTo(3);
      }
    } finally {
      try (var connection =
              DriverManager.getConnection(
                  POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
          var sql = connection.createStatement()) {
        sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }
}
