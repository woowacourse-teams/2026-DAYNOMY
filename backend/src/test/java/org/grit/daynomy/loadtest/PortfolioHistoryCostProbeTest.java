package org.grit.daynomy.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.hibernate.Session;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** Opt-in diagnostic, not an HTTP benchmark. Uses only disposable PostgreSQL fixtures. */
@EnabledIfEnvironmentVariable(named = "HISTORY_COST_PROBE", matches = "true")
@ActiveProfiles("test")
@DataJpaTest(
    properties = {
      "spring.jpa.show-sql=false",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=org.grit.daynomy.loadtest.PortfolioHistoryCostProbeTest$SqlCapture"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioHistoryCostProbeTest extends SharedDatabaseTest {
  @Autowired private TestEntityManager em;
  @Autowired private PortfolioHoldingHistoryRepository repository;
  private static final ThreadMXBean ALLOC = (ThreadMXBean) ManagementFactory.getThreadMXBean();

  @ParameterizedTest
  @ValueSource(ints = {10, 100, 500})
  void separatesServerExecutionMaterializationAndReplay(int count) throws Exception {
    String key = UUID.randomUUID().toString();
    Portfolio portfolio =
        em.persist(
            Portfolio.create(
                em.persist(
                    Member.createGoogleMember(
                        key, key + "@example.invalid", key.substring(0, 12), null))));
    Asset asset =
        em.persist(
            Asset.listedStock(
                "할당 계측",
                key,
                StockMarket.KOSPI,
                "KR" + key.replace("-", "").substring(0, 10),
                LocalDate.of(2026, 9, 1)));
    for (int i = 0; i < count; i++) {
      em.persist(
          new PortfolioHoldingHistory(
              portfolio,
              asset,
              PortfolioHoldingChangeType.BUY,
              i,
              new BigDecimal("100.00"),
              LocalDate.of(2026, 9, 1),
              1L,
              new BigDecimal("100.0000"),
              BigDecimal.ZERO,
              BigDecimal.ZERO));
    }
    em.flush();
    Long id = portfolio.getId();
    Long assetId = asset.getId();
    em.clear();
    assertThat(repository.findOrderedWithAssetsByPortfolioId(id)).hasSize(count);
    String sql = SqlCapture.historySql.get();
    assertThat(sql).isNotNull();
    var session = em.getEntityManager().unwrap(Session.class);
    var plan =
        session.doReturningWork(
            connection -> {
              try (var statement =
                  connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql)) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getString(1);
                }
              }
            });
    List<Sample> jpa = new ArrayList<>();
    List<Sample> jdbc = new ArrayList<>();
    List<Sample> replay = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      em.clear();
      long bytes = bytes();
      long start = System.nanoTime();
      var histories = repository.findOrderedWithAssetsByPortfolioId(id);
      Sample read = new Sample(System.nanoTime() - start, bytes() - bytes);
      assertThat(histories).hasSize(count);
      bytes = bytes();
      start = System.nanoTime();
      var ledger = new PortfolioLedgerCalculator().replay(histories);
      Sample calculation = new Sample(System.nanoTime() - start, bytes() - bytes);
      assertThat(ledger.positions().get(assetId).quantity()).isEqualTo(count);
      Sample raw =
          session.doReturningWork(
              connection -> {
                long before = bytes();
                long began = System.nanoTime();
                int consumed = 0;
                try (var statement = connection.prepareStatement(sql)) {
                  statement.setLong(1, id);
                  try (var rows = statement.executeQuery()) {
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                      for (int column = 1; column <= columns; column++) rows.getObject(column);
                      consumed++;
                    }
                  }
                }
                if (consumed != count) throw new IllegalStateException("Unexpected result size");
                return new Sample(System.nanoTime() - began, bytes() - before);
              });
      if (i >= 5) {
        jpa.add(read);
        jdbc.add(raw);
        replay.add(calculation);
      }
    }
    if (count == 500 && "true".equals(System.getenv("HISTORY_LOCAL_JFR"))) {
      java.nio.file.Path destination = java.nio.file.Path.of("build", "history-jpa-only.jfr");
      var configuration =
          jdk.jfr.Configuration.create(
              java.nio.file.Path.of("loadtest", "profiles", "history-allocation.jfc"));
      try (var recording = new jdk.jfr.Recording(configuration)) {
        recording.start();
        for (int i = 0; i < 250; i++) {
          em.clear();
          assertThat(repository.findOrderedWithAssetsByPortfolioId(id)).hasSize(count);
        }
        java.time.Instant profileStart = java.time.Instant.now();
        for (int i = 0; i < 250; i++) {
          em.clear();
          assertThat(repository.findOrderedWithAssetsByPortfolioId(id)).hasSize(count);
        }
        java.time.Instant profileEnd = java.time.Instant.now();
        recording.stop();
        recording.dump(destination);
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("build", "history-jpa-window.json"),
            "{\"start\":\"" + profileStart + "\",\"end\":\"" + profileEnd + "\"}");
      }
    }
    System.out.println("HISTORY_SQL " + sql);
    System.out.println(
        "HISTORY_COST {\"historyCount\":"
            + count
            + ",\"warmups\":5,\"samples\":20,\"jpa\":"
            + summarize(jpa)
            + ",\"jdbcAllColumns\":"
            + summarize(jdbc)
            + ",\"replay\":"
            + summarize(replay)
            + ",\"plan\":"
            + plan
            + "}");
  }

  @Test
  void validatesActualFixedFixtureSqlAgainstDisposablePostgres() throws Exception {
    String key = UUID.randomUUID().toString();
    em.persist(
        Asset.listedStock(
            "격리된 계측 종목",
            key,
            StockMarket.KOSPI,
            "KR" + key.replace("-", "").substring(0, 10),
            LocalDate.of(2026, 9, 1)));
    em.flush();
    // The Python child uses another connection: publish only this synthetic asset for this probe.
    org.springframework.test.context.transaction.TestTransaction.flagForCommit();
    org.springframework.test.context.transaction.TestTransaction.end();
    var command =
        new ProcessBuilder(
            "python3",
            "-m",
            "unittest",
            "discover",
            "-s",
            "loadtest",
            "-p",
            "test_fixed_history_fixture.py");
    var env = command.environment();
    env.put("FIXED_HISTORY_DISPOSABLE_DB", "true");
    env.put("PGHOST", POSTGRESQL.getHost());
    env.put("PGPORT", Integer.toString(POSTGRESQL.getMappedPort(5432)));
    env.put("PGDATABASE", POSTGRESQL.getDatabaseName());
    env.put("PGUSER", POSTGRESQL.getUsername());
    env.put("PGPASSWORD", POSTGRESQL.getPassword());
    var child = command.redirectErrorStream(true).start();
    assertThat(child.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    String output =
        new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(child.exitValue())
        .withFailMessage("Fixture SQL lifecycle failed (details withheld)")
        .isZero();
    assertThat(output).contains("OK");
  }

  private static long bytes() {
    return ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId());
  }

  private record Sample(long nanos, long allocatedBytes) {}

  private static String summarize(List<Sample> samples) {
    long[] times = samples.stream().mapToLong(Sample::nanos).sorted().toArray();
    long[] bytes = samples.stream().mapToLong(Sample::allocatedBytes).sorted().toArray();
    return "{\"medianMs\":"
        + (times[10] + times[9]) / 2e6
        + ",\"medianAllocatedBytes\":"
        + (bytes[10] + bytes[9]) / 2
        + "}";
  }

  public static class SqlCapture implements StatementInspector {
    static final ThreadLocal<String> historySql = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
      if (sql.startsWith("select ")
          && sql.contains("from portfolio_holding_histories")
          && sql.contains("join assets")
          && sql.contains("order by")) historySql.set(sql);
      return sql;
    }
  }
}
