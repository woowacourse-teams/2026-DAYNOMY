package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "NEWS_PUBLISH_BENCHMARK", matches = "true")
@ActiveProfiles("test")
@SpringBootTest
@Import(NewsPublishConnectionBenchmarkTest.BenchmarkConfiguration.class)
class NewsPublishConnectionBenchmarkTest extends SharedDatabaseTest {

  private static final int CONCURRENT_PUBLISHES = 4;

  @Autowired private AdminNewsService adminNewsService;
  @Autowired private NewsRepository newsRepository;
  @Autowired private KeywordService keywordService;
  @Autowired private MarketAnalysisService marketAnalysisService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DelayedKeywordAiClient keywordAiClient;
  @Autowired private DelayedMarketAnalysisAiClient marketAnalysisAiClient;
  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void benchmarkPool(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
    registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
  }

  @Test
  void compareActiveConnectionsDuringAiWait() throws Exception {
    PoolSnapshot legacy =
        measure(this::publishWithLegacyTransaction, CONCURRENT_PUBLISHES, CONCURRENT_PUBLISHES);
    PoolSnapshot split = measure(adminNewsService::publish, 2, 0);
    long legacyDurationMs = measureDuration(this::publishWithLegacyTransaction);
    long parallelDurationMs = measureDuration(adminNewsService::publish);

    assertThat(legacy.active()).isGreaterThanOrEqualTo(CONCURRENT_PUBLISHES);
    assertThat(split.active()).isZero();

    String report =
        """
        concurrent_publishes=%d
        legacy_active_connections=%d
        legacy_pending_connections=%d
        split_active_connections=%d
        split_pending_connections=%d
        active_connection_reduction=%d
        legacy_duration_ms=%d
        parallel_duration_ms=%d
        duration_reduction_ms=%d
        """
            .formatted(
                CONCURRENT_PUBLISHES,
                legacy.active(),
                legacy.pending(),
                split.active(),
                split.pending(),
                legacy.active() - split.active(),
                legacyDurationMs,
                parallelDurationMs,
                legacyDurationMs - parallelDurationMs);
    Path reportPath = Path.of("build/reports/news-publish-connection-benchmark.txt");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, report);
    System.out.println(report);
  }

  private PoolSnapshot measure(PublishAction action, int expectedAiCalls, int expectedActive)
      throws Exception {
    List<Long> newsIds = createDrafts(CONCURRENT_PUBLISHES);
    keywordAiClient.block(expectedAiCalls);
    marketAnalysisAiClient.delayFor(0);
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_PUBLISHES);
    List<Future<?>> publishes = new ArrayList<>();
    try {
      newsIds.forEach(id -> publishes.add(executor.submit(() -> action.publish(id))));
      keywordAiClient.awaitCalls();
      HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
      awaitActiveConnections(pool, expectedActive);
      PoolSnapshot snapshot =
          new PoolSnapshot(pool.getActiveConnections(), pool.getThreadsAwaitingConnection());
      keywordAiClient.resume();
      for (Future<?> publish : publishes) {
        publish.get(10, TimeUnit.SECONDS);
      }
      return snapshot;
    } finally {
      keywordAiClient.resume();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  private long measureDuration(PublishAction action) {
    Long newsId = createDrafts(1).getFirst();
    keywordAiClient.delayFor(700);
    marketAnalysisAiClient.delayFor(700);
    long startedAt = System.nanoTime();
    try {
      action.publish(newsId);
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    } finally {
      keywordAiClient.delayFor(0);
      marketAnalysisAiClient.delayFor(0);
    }
  }

  private void awaitActiveConnections(HikariPoolMXBean pool, int expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (pool.getActiveConnections() != expected && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(pool.getActiveConnections()).isEqualTo(expected);
  }

  private List<Long> createDrafts(int count) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status ->
                newsRepository
                    .saveAll(
                        java.util.stream.IntStream.range(0, count)
                            .mapToObj(
                                index ->
                                    News.createDraft(
                                        "벤치마크 뉴스 " + index,
                                        "벤치마크 본문 " + index,
                                        null,
                                        List.of(
                                            new NewsSourceInfo(
                                                "벤치마크", "https://benchmark.invalid/news/" + index)),
                                        Category.STOCK))
                            .toList())
                    .stream()
                    .map(News::getId)
                    .toList());
  }

  private void publishWithLegacyTransaction(Long id) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              News news = newsRepository.findById(id).orElseThrow();
              List<NewsKeyword> keywords = keywordAiClient.extractKeywords(news.getContent());
              NewsMarketAnalysis analysis = marketAnalysisAiClient.analyze(news.getContent());
              keywordService.saveKeywords(news, keywords);
              marketAnalysisService.saveMarketAnalysis(news, analysis);
              news.publish();
            });
  }

  @FunctionalInterface
  private interface PublishAction {
    void publish(Long id);
  }

  private record PoolSnapshot(int active, int pending) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class BenchmarkConfiguration {

    @Bean
    @Primary
    DelayedKeywordAiClient delayedKeywordAiClient() {
      return new DelayedKeywordAiClient();
    }

    @Bean
    @Primary
    DelayedMarketAnalysisAiClient benchmarkMarketAnalysisAiClient() {
      return new DelayedMarketAnalysisAiClient();
    }
  }

  static final class DelayedKeywordAiClient implements KeywordAiClient {

    private volatile CountDownLatch calls = new CountDownLatch(0);
    private volatile CountDownLatch resume = new CountDownLatch(0);
    private volatile long delayMillis;

    void block(int expectedCalls) {
      calls = new CountDownLatch(expectedCalls);
      resume = new CountDownLatch(1);
      delayMillis = 0;
    }

    void delayFor(long delayMillis) {
      calls = new CountDownLatch(0);
      resume = new CountDownLatch(0);
      this.delayMillis = delayMillis;
    }

    void awaitCalls() throws InterruptedException {
      assertThat(calls.await(10, TimeUnit.SECONDS)).isTrue();
    }

    void resume() {
      resume.countDown();
    }

    @Override
    public List<NewsKeyword> extractKeywords(String newsContent) {
      calls.countDown();
      try {
        if (!resume.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out while delaying the benchmark AI client.");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Benchmark AI client was interrupted.", exception);
      }
      sleep(delayMillis);
      return List.of(
          new NewsKeyword(KeywordCategory.POLICY, "벤치마크", "벤치마크 정의", "벤치마크 맥락", "벤치마크 관찰점"));
    }
  }

  static final class DelayedMarketAnalysisAiClient implements MarketAnalysisAiClient {

    private volatile long delayMillis;

    void delayFor(long delayMillis) {
      this.delayMillis = delayMillis;
    }

    @Override
    public NewsMarketAnalysis analyze(String newsContent) {
      sleep(delayMillis);
      return new NewsMarketAnalysis("벤치마크 위험", "벤치마크 관찰점");
    }
  }

  private static void sleep(long delayMillis) {
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Benchmark AI client was interrupted.", exception);
    }
  }
}
