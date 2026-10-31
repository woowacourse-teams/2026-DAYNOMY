package org.grit.daynomy.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Portfolio1000LoadtestToolTest {
  private static final String NS = "lt1000-20261029";
  private static final String ORIGIN = "https://dev.daynomy.com";

  @Container
  static final PostgreSQLContainer DATABASE =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
          .withDatabaseName("daynomy_dev");

  @TempDir Path temporary;

  @Test
  void issuesAllTokensAndRestoresOnlyTheCohortWhileRejectingChangedReferencesAndFiles()
      throws Exception {
    try (Connection c =
            DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        var s = c.createStatement()) {
      for (String migration :
          new String[] {
            "V1__init_schema.sql",
            "V11__add_stock_master_columns.sql",
            "V12__create_stock_daily_prices.sql",
            "V15__create_portfolio_tracking.sql",
            "V30__separate_private_google_name.sql",
            "V33__add_portfolio_transaction_ledger.sql",
            "V51__preserve_us_stock_price_precision.sql",
            "V53__support_usd_portfolio_transactions.sql"
          }) s.execute(Files.readString(Path.of("src/main/resources/db/migration", migration)));
      s.execute(
          """
          INSERT INTO members(provider,provider_id,email,nickname,role,status,created_at,updated_at)
          SELECT 'GOOGLE','lt1000-20261029-'||n,'lt1000-20261029-'||n||'@example.invalid',
            'lt1000-20261029-'||n,'USER','ACTIVE',now(),now() FROM generate_series(1,1003)n;
          INSERT INTO members(provider,provider_id,email,nickname,role,status,created_at,updated_at)
          VALUES('GOOGLE','unrelated','unrelated@example.invalid','unrelated','USER','ACTIVE',now(),now());
          INSERT INTO portfolios(member_id,created_at,updated_at) SELECT id,now(),now() FROM members;
          INSERT INTO assets(name,category,asset_code,market,listed,created_at,updated_at)
          SELECT 'Fixture'||n,'STOCK','FIX'||n,CASE WHEN n%2=0 THEN 'NASDAQ' ELSE 'KOSPI' END,true,now(),now() FROM generate_series(1,30)n;
          CREATE TEMP TABLE profiles AS SELECT p.id,CASE WHEN p.id=1001 THEN 5 WHEN p.id=1002 THEN 15 WHEN p.id=1003 THEN 30 WHEN p.id%5 IN(1,3) THEN 5 WHEN p.id%5 IN(2,4) THEN 15 ELSE 30 END holdings,
            CASE WHEN p.id=1001 THEN 50 WHEN p.id=1002 THEN 200 WHEN p.id=1003 THEN 800 WHEN p.id%5 IN(1,3) THEN 50 WHEN p.id%5 IN(2,4) THEN 200 ELSE 800 END trades
            FROM portfolios p WHERE p.id<=1003;
          INSERT INTO portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price,created_at,updated_at)
          SELECT p.id,a,100+CASE WHEN a=2 THEN p.trades ELSE 0 END,CASE WHEN a%2=0 THEN 26000 ELSE 10000 END,now(),now()
          FROM profiles p CROSS JOIN LATERAL generate_series(1,p.holdings)a;
          INSERT INTO portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price,created_at,updated_at)
          VALUES(1004,1,99,10000,now(),now());
          INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,effective_date,created_at,updated_at)
          SELECT p.id,a,'OPENING_BALANCE',100,CASE WHEN a%2=0 THEN 26000 ELSE 10000 END,DATE '2025-01-01',now(),now()
          FROM profiles p CROSS JOIN LATERAL generate_series(1,p.holdings)a;
          INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,effective_date,transaction_quantity,unit_price,exchange_rate,created_at,updated_at)
          SELECT p.id,2,'BUY',100+n-1,26000,DATE '2026-09-29',1,20,1300,now()+n*interval '1 ms',now()
          FROM profiles p CROSS JOIN LATERAL generate_series(1,p.trades)n;
          INSERT INTO stock_daily_prices(asset_id,base_date,close_price,created_at,updated_at)
          SELECT a.id,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-n,CASE WHEN a.market='NASDAQ' THEN 20 ELSE 10000 END,now(),now()
          FROM assets a CROSS JOIN generate_series(1,2)n;
          INSERT INTO usd_krw_rates SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-n,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-n,1300,now() FROM generate_series(1,2)n;
          INSERT INTO portfolio_daily_snapshots(portfolio_id,base_date,total_purchase_amount,total_evaluation_amount,total_profit_loss,total_return_rate,created_at,updated_at)
          SELECT p.id,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-n,10000,10000,0,0,now(),now()
          FROM profiles p CROSS JOIN generate_series(1,2)n;
          """);
      c.setAutoCommit(false);
      s.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
      Portfolio1000LoadtestTool.validateCohort(c, NS);
      Path baseline = temporary.resolve("baseline");
      var manifest = Portfolio1000LoadtestTool.capture(c, NS, ORIGIN, "test-target", baseline);
      c.commit();
      s.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
      var issuer =
          new JwtTokenProvider(
              "daynomy", Base64.getEncoder().encodeToString(new byte[64]), 86400000, 7200000);
      var accounts =
          Portfolio1000LoadtestTool.accounts(
              c, NS, ORIGIN, manifest.path("identity").asText(), issuer);
      assertThat(accounts.size()).isEqualTo(1003);
      for (var account : accounts) {
        var jwt = issuer.parseAccessToken(account.path("accessToken").asText());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(86400));
        assertThat(issuer.getMemberId(jwt)).isEqualTo(account.path("memberId").asLong());
        assertThat(account.path("currency").asText()).isEqualTo("USD");
        assertThat(account.path("country").asText()).isEqualTo("MIXED");
        assertThat(account.path("exchangeRate").decimalValue()).isEqualByComparingTo("1300");
        assertThat(account.path("holdings").size())
            .isEqualTo(
                Portfolio1000LoadtestTool.profileHoldings(account.path("testNumber").asInt()));
        assertThat(account.path("evaluationDate").asText())
            .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        assertThat(account.path("transactionBaselines").path("0").path("date").asText())
            .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
      }
      assertThat(accounts.get(1000).path("profile").asText()).isEqualTo("light");
      assertThat(accounts.get(1002).path("profile").asText()).isEqualTo("heavy");
      assertThat(Files.readString(baseline.resolve("manifest.json"))).doesNotContain("accessToken");
      s.execute("UPDATE portfolio_holdings SET quantity=quantity+30 WHERE portfolio_id=1");
      c.commit();
      s.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
      Portfolio1000LoadtestTool.validateManifest(c, NS, ORIGIN, "test-target", baseline, manifest);
      Portfolio1000LoadtestTool.restore(c, NS, baseline, manifest);
      c.commit();
      try (var rows =
          s.executeQuery(
              "SELECT quantity FROM portfolio_holdings WHERE portfolio_id=1 AND asset_id=2")) {
        rows.next();
        assertThat(rows.getLong(1)).isEqualTo(150);
      }
      try (var rows =
          s.executeQuery("SELECT quantity FROM portfolio_holdings WHERE portfolio_id=1004")) {
        rows.next();
        assertThat(rows.getLong(1)).isEqualTo(99);
      }
      s.execute("UPDATE stock_daily_prices SET close_price=21 WHERE asset_id=2");
      assertThatThrownBy(
              () ->
                  Portfolio1000LoadtestTool.validateManifest(
                      c, NS, ORIGIN, "test-target", baseline, manifest))
          .hasMessageContaining("reference data changed");
      c.rollback();
      s.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
      Path file = baseline.resolve("portfolio_holdings.jsonl.gz");
      byte[] original = Files.readAllBytes(file);
      Files.writeString(file, "corrupt");
      assertThatThrownBy(
              () ->
                  Portfolio1000LoadtestTool.validateManifest(
                      c, NS, ORIGIN, "test-target", baseline, manifest))
          .hasMessageContaining("corrupted");
      Files.write(file, original);
      s.execute("UPDATE members SET status='WITHDRAWN' WHERE id=1");
      assertThatThrownBy(() -> Portfolio1000LoadtestTool.validateCohort(c, NS))
          .hasMessageContaining("active test USER");
      c.rollback();
    }
  }
}
