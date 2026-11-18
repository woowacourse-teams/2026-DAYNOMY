package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(named = "NEWS_PUBLISH_OPENAI_BENCHMARK", matches = "true")
@ActiveProfiles("test")
@SpringBootTest
class NewsPublishOpenAiBenchmarkTest extends SharedDatabaseTest {

  private static final String CONTENT =
      """
      한국은행 금융통화위원회는 기준금리를 현재 수준으로 동결했어요. 위원회는 물가 상승세가 둔화하고 있지만 환율 변동성과 가계대출 증가세가 남아 있다고 설명했어요.

      수출은 반도체를 중심으로 회복 흐름을 보였지만 내수 회복 속도는 완만했어요. 한국은행은 향후 물가와 성장 경로, 금융안정 상황을 함께 살펴 통화정책을 결정할 계획이라고 밝혔어요.

      시장에서는 기준금리 인하 시점에 대한 전망이 엇갈렸어요. 일부 참여자는 물가 둔화가 이어지면 완화 가능성이 커질 것으로 봤지만, 다른 참여자는 환율과 가계대출 흐름을 더 확인해야 한다고 평가했어요.
      """;

  @Autowired private KeywordAiClient keywordAiClient;
  @Autowired private MarketAnalysisAiClient marketAnalysisAiClient;

  @Autowired
  @Qualifier("newsPublishAiExecutor")
  private Executor executor;

  @Test
  void measureRealOpenAiCalls() throws Exception {
    List<Measurement> sequential = new ArrayList<>();
    List<Measurement> parallel = new ArrayList<>();

    sequential.add(measureSequential());
    parallel.add(measureParallel());
    parallel.add(measureParallel());
    sequential.add(measureSequential());

    long sequentialAverage = averageTotal(sequential);
    long parallelAverage = averageTotal(parallel);
    String report = report(sequential, parallel, sequentialAverage, parallelAverage);

    assertThat(sequential).allSatisfy(Measurement::assertSuccessful);
    assertThat(parallel).allSatisfy(Measurement::assertSuccessful);

    Path reportPath = Path.of("build/reports/news-publish-openai-benchmark.txt");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, report);
    System.out.println(report);
  }

  private Measurement measureSequential() {
    long startedAt = System.nanoTime();
    Timed<?> keywords = measure(() -> keywordAiClient.extractKeywords(CONTENT));
    Timed<?> market = measure(() -> marketAnalysisAiClient.analyze(CONTENT));
    return new Measurement(keywords, market, elapsedMillis(startedAt));
  }

  private Measurement measureParallel() throws Exception {
    long startedAt = System.nanoTime();
    CompletableFuture<Timed<?>> keywords =
        CompletableFuture.supplyAsync(
            () -> measure(() -> keywordAiClient.extractKeywords(CONTENT)), executor);
    CompletableFuture<Timed<?>> market =
        CompletableFuture.supplyAsync(
            () -> measure(() -> marketAnalysisAiClient.analyze(CONTENT)), executor);
    return new Measurement(keywords.get(), market.get(), elapsedMillis(startedAt));
  }

  private Timed<?> measure(Supplier<?> request) {
    long startedAt = System.nanoTime();
    Object result = request.get();
    return new Timed<>(result, elapsedMillis(startedAt));
  }

  private long averageTotal(List<Measurement> measurements) {
    return Math.round(measurements.stream().mapToLong(Measurement::totalMs).average().orElseThrow());
  }

  private String report(
      List<Measurement> sequential,
      List<Measurement> parallel,
      long sequentialAverage,
      long parallelAverage) {
    return """
        model=%s
        sequential_round_1_keyword_ms=%d
        sequential_round_1_market_ms=%d
        sequential_round_1_total_ms=%d
        sequential_round_2_keyword_ms=%d
        sequential_round_2_market_ms=%d
        sequential_round_2_total_ms=%d
        parallel_round_1_keyword_ms=%d
        parallel_round_1_market_ms=%d
        parallel_round_1_total_ms=%d
        parallel_round_2_keyword_ms=%d
        parallel_round_2_market_ms=%d
        parallel_round_2_total_ms=%d
        sequential_average_total_ms=%d
        parallel_average_total_ms=%d
        average_reduction_ms=%d
        average_reduction_percent=%.1f
        """
        .formatted(
            System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5-mini"),
            sequential.get(0).keywords().durationMs(),
            sequential.get(0).market().durationMs(),
            sequential.get(0).totalMs(),
            sequential.get(1).keywords().durationMs(),
            sequential.get(1).market().durationMs(),
            sequential.get(1).totalMs(),
            parallel.get(0).keywords().durationMs(),
            parallel.get(0).market().durationMs(),
            parallel.get(0).totalMs(),
            parallel.get(1).keywords().durationMs(),
            parallel.get(1).market().durationMs(),
            parallel.get(1).totalMs(),
            sequentialAverage,
            parallelAverage,
            sequentialAverage - parallelAverage,
            (sequentialAverage - parallelAverage) * 100.0 / sequentialAverage);
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private record Timed<T>(T result, long durationMs) {}

  private record Measurement(Timed<?> keywords, Timed<?> market, long totalMs) {
    void assertSuccessful() {
      assertThat(keywords.result()).isNotNull();
      assertThat(market.result()).isNotNull();
    }
  }
}
