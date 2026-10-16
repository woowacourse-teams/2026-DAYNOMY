package org.grit.daynomy.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PortfolioLoadtestDatabaseTest {
  private static final String NS = "lt496-20261015";

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  private Connection connect() throws Exception {
    return DriverManager.getConnection(
        POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
  }

  @BeforeEach
  void initialize() throws Exception {
    try (Connection c = connect();
        var s = c.createStatement()) {
      s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
      for (String migration :
          new String[] {
            "V1__init_schema.sql",
            "V11__add_stock_master_columns.sql",
            "V12__create_stock_daily_prices.sql",
            "V15__create_portfolio_tracking.sql",
            "V30__separate_private_google_name.sql",
            "V33__add_portfolio_transaction_ledger.sql"
          }) {
        s.execute(Files.readString(Path.of("src/main/resources/db/migration", migration)));
      }
      s.execute(
          """
        INSERT INTO members (provider,provider_id,email,nickname,role,status,created_at,updated_at) VALUES
          ('GOOGLE','lt496-20261015-1','lt496-20261015-1@example.invalid','test1','USER','ACTIVE',now(),now()),
          ('GOOGLE','lt496-20261015-21','lt496-20261015-21@example.invalid','test21','USER','ACTIVE',now(),now()),
          ('GOOGLE','other','other@example.invalid','other','USER','ACTIVE',now(),now());
        INSERT INTO portfolios (member_id,created_at,updated_at) SELECT id,now(),now() FROM members;
        INSERT INTO assets (name,category,asset_code,created_at,updated_at) VALUES ('Stock','STOCK','CHECK',now(),now());
        INSERT INTO stock_daily_prices (asset_id,base_date,close_price,created_at,updated_at) VALUES (1,'2026-10-14',10000.25,now(),now());
        INSERT INTO portfolio_holdings (portfolio_id,asset_id,quantity,average_purchase_price,created_at,updated_at)
          SELECT id,1,100,123456789012345.67,now(),now() FROM portfolios;
        INSERT INTO portfolio_holding_histories (portfolio_id,asset_id,change_type,quantity,average_purchase_price,effective_date,created_at,updated_at)
          SELECT id,1,'OPENING_BALANCE',100,123456789012345.67,'2026-04-01',now(),now() FROM portfolios;
        INSERT INTO portfolio_daily_snapshots (portfolio_id,base_date,total_purchase_amount,total_evaluation_amount,total_profit_loss,total_return_rate,created_at,updated_at)
          SELECT id,'2026-10-14',100000,100000,0,0,now(),now() FROM portfolios;
        """);
    }
  }

  @Test
  void restoresExactBaselineWithoutTouchingOtherMembersOrResettingSequences() throws Exception {
    try (Connection c = connect()) {
      JsonNode baseline = PortfolioLoadtestTool.readData(c, NS);
      try (var s = c.createStatement()) {
        s.execute(
            "UPDATE portfolio_holdings SET quantity=200; DELETE FROM portfolio_daily_snapshots WHERE portfolio_id=1");
        s.execute(
            "INSERT INTO portfolio_holding_histories (portfolio_id,asset_id,change_type,quantity,average_purchase_price,effective_date,created_at,updated_at) VALUES (1,1,'ADJUSTED',200,1000,'2026-10-15',now(),now())");
      }
      long sequenceBefore = scalar(c, "SELECT last_value FROM portfolio_holding_histories_id_seq");
      c.setAutoCommit(false);
      PortfolioLoadtestTool.restore(c, NS, baseline);
      c.commit();
      assertThat(PortfolioLoadtestTool.readData(c, NS)).isEqualTo(baseline);
      assertThat(scalar(c, "SELECT quantity FROM portfolio_holdings WHERE portfolio_id=3"))
          .isEqualTo(200);
      assertThat(scalar(c, "SELECT last_value FROM portfolio_holding_histories_id_seq"))
          .isEqualTo(sequenceBefore);
      assertThat(baseline.path("holdings").get(0).path("average_purchase_price").decimalValue())
          .isEqualByComparingTo("123456789012345.67");
    }
  }

  @Test
  void restoresSerializedBaselineWithoutTreatingWholeDecimalsAsChangedData() throws Exception {
    try (Connection c = connect()) {
      JsonNode captured = PortfolioLoadtestTool.readData(c, NS);
      JsonNode serialized =
          PortfolioLoadtestTool.JSON.readTree(
              PortfolioLoadtestTool.JSON
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(captured));
      assertThat(serialized).isNotEqualTo(captured);

      c.setAutoCommit(false);
      PortfolioLoadtestTool.restore(c, NS, serialized);
      c.commit();

      assertThat(PortfolioLoadtestTool.readData(c, NS)).isEqualTo(captured);
    }
  }

  @Test
  void seedsFiftyMeasurementMembersAndThreeWarmupMembersWithoutChangingExistingMembers()
      throws Exception {
    String namespace = "lt496-20261013";
    try (Connection c = connect();
        var s = c.createStatement()) {
      s.execute(
          """
        INSERT INTO assets (name,category,asset_code,created_at,updated_at)
          SELECT 'Seed Stock '||n,'STOCK','SEED'||lpad(n::text,3,'0'),now(),now()
          FROM generate_series(1,30) n;
        INSERT INTO stock_daily_prices (asset_id,base_date,close_price,created_at,updated_at)
          SELECT a.id,d::date,10000+a.id*100,now(),now()
          FROM assets a CROSS JOIN generate_series(DATE '2025-10-01',DATE '2026-10-12',INTERVAL '1 day') d
          WHERE a.asset_code LIKE 'SEED%' AND extract(isodow FROM d) <= 5;
        """);
      String sql =
          Files.readString(Path.of("loadtest/sql/seed-portfolios.sql"))
              .replace(
                  "-- UPDATE lt_config SET as_of = DATE '2026-10-15';",
                  "UPDATE lt_config SET as_of = DATE '2026-10-13';");
      s.execute(sql);
      JsonNode data = PortfolioLoadtestTool.readData(c, namespace);
      PortfolioLoadtestTool.validateCohort(data, namespace);
      assertThat(data.path("members").size()).isEqualTo(53);
      assertThat(data.path("holdings").size()).isEqualTo(750);
      assertThat(data.path("histories").size()).isEqualTo(14800);
      assertThat(scalar(c, "SELECT quantity FROM portfolio_holdings WHERE portfolio_id=3"))
          .isEqualTo(100);
      assertThat(scalar(c, "SELECT count(*) FROM members")).isEqualTo(56);
      assertThatThrownBy(() -> s.execute(sql)).hasMessageContaining("이미 있습니다");
    }
  }

  @Test
  void refusesRestoreWhileAnotherConnectionOwnsATestMemberLock() throws Exception {
    try (Connection active = connect();
        Connection restoring = connect()) {
      JsonNode baseline = PortfolioLoadtestTool.readData(restoring, NS);
      active.setAutoCommit(false);
      try (var statement = active.createStatement()) {
        statement.execute("SELECT id FROM members WHERE id=1 FOR UPDATE");
      }
      restoring.setAutoCommit(false);
      assertThatThrownBy(() -> PortfolioLoadtestTool.restore(restoring, NS, baseline))
          .isInstanceOf(java.sql.SQLException.class);
      restoring.rollback();
      active.rollback();
      assertThat(PortfolioLoadtestTool.readData(restoring, NS)).isEqualTo(baseline);
    }
  }

  @Test
  void refusesChangedPriceContextBeforeDeletingAnyTestData() throws Exception {
    try (Connection c = connect()) {
      JsonNode baseline = PortfolioLoadtestTool.readData(c, NS);
      try (var s = c.createStatement()) {
        s.execute(
            "UPDATE stock_daily_prices SET close_price=999; UPDATE portfolio_holdings SET quantity=200 WHERE portfolio_id=1");
      }
      c.setAutoCommit(false);
      assertThatThrownBy(() -> PortfolioLoadtestTool.restore(c, NS, baseline))
          .hasMessageContaining("Price data changed");
      c.rollback();
      assertThat(scalar(c, "SELECT quantity FROM portfolio_holdings WHERE portfolio_id=1"))
          .isEqualTo(200);
    }
  }

  @Test
  void refusesMemberIdentityChangesBeforeDeletingAnyTestData() throws Exception {
    try (Connection c = connect()) {
      JsonNode baseline = PortfolioLoadtestTool.readData(c, NS);
      try (var s = c.createStatement()) {
        s.execute(
            "UPDATE members SET provider_id='changed' WHERE id=1; UPDATE portfolio_holdings SET quantity=200 WHERE portfolio_id=1");
      }
      c.setAutoCommit(false);
      assertThatThrownBy(() -> PortfolioLoadtestTool.restore(c, NS, baseline))
          .hasMessageContaining("identity changed");
      c.rollback();
      assertThat(scalar(c, "SELECT quantity FROM portfolio_holdings WHERE portfolio_id=1"))
          .isEqualTo(200);
    }
  }

  private long scalar(Connection c, String sql) throws Exception {
    try (var s = c.createStatement();
        var r = s.executeQuery(sql)) {
      r.next();
      return r.getLong(1);
    }
  }
}
