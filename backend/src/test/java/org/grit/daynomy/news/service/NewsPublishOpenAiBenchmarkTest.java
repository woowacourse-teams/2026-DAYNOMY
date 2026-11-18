package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

  @Autowired private AdminNewsService adminNewsService;
  @Autowired private NewsRepository newsRepository;
  @Autowired private KeywordAiClient keywordAiClient;
  @Autowired private MarketAnalysisAiClient marketAnalysisAiClient;
  @Autowired private KeywordService keywordService;
  @Autowired private MarketAnalysisService marketAnalysisService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Value("${ai.openai.model:gpt-5-mini}")
  private String model;

  @Test
  void measureRealNewsPublish() throws Exception {
    List<Measurement> sequential = new ArrayList<>();
    List<Measurement> parallel = new ArrayList<>();

    sequential.add(measureSequentialPublish());
    parallel.add(measureParallelPublish());
    parallel.add(measureParallelPublish());
    sequential.add(measureSequentialPublish());

    long sequentialAverage = averageTotal(sequential);
    long parallelAverage = averageTotal(parallel);
    String report = report(sequential, parallel, sequentialAverage, parallelAverage);

    assertThat(sequential).allSatisfy(this::assertPublished);
    assertThat(parallel).allSatisfy(this::assertPublished);

    Path reportPath = Path.of("build/reports/news-publish-openai-benchmark.txt");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, report);
    System.out.println(report);
  }

  private Measurement measureSequentialPublish() {
    Long newsId = createDraft();
    long startedAt = System.nanoTime();
    publishSequentially(newsId);
    return new Measurement(newsId, elapsedMillis(startedAt));
  }

  private Measurement measureParallelPublish() {
    Long newsId = createDraft();
    long startedAt = System.nanoTime();
    adminNewsService.publish(newsId);
    return new Measurement(newsId, elapsedMillis(startedAt));
  }

  private Long createDraft() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status ->
                newsRepository
                    .save(
                        News.createDraft(
                            "뉴스 발행 실측",
                            CONTENT,
                            null,
                            List.of(
                                new NewsSourceInfo(
                                    "벤치마크", "https://benchmark.invalid/news/publish")),
                            Category.STOCK))
                    .getId());
  }

  private void publishSequentially(Long newsId) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              News news = newsRepository.findById(newsId).orElseThrow();
              List<NewsKeyword> keywords = keywordAiClient.extractKeywords(news.getContent());
              NewsMarketAnalysis marketAnalysis = marketAnalysisAiClient.analyze(news.getContent());
              keywordService.saveKeywords(news, keywords);
              marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);
              newsRepository.flush();
              news.publish();
            });
  }

  private long averageTotal(List<Measurement> measurements) {
    return Math.round(
        measurements.stream().mapToLong(Measurement::totalMs).average().orElseThrow());
  }

  private String report(
      List<Measurement> sequential,
      List<Measurement> parallel,
      long sequentialAverage,
      long parallelAverage) {
    return """
        model=%s
        measurement_scope=service_publish_with_database
        sequential_path=legacy_single_transaction_sequential_ai
        parallel_path=split_transaction_parallel_ai
        sequential_round_1_total_ms=%d
        sequential_round_2_total_ms=%d
        parallel_round_1_total_ms=%d
        parallel_round_2_total_ms=%d
        sequential_average_total_ms=%d
        parallel_average_total_ms=%d
        average_reduction_ms=%d
        average_reduction_percent=%.1f
        """
        .formatted(
            model,
            sequential.get(0).totalMs(),
            sequential.get(1).totalMs(),
            parallel.get(0).totalMs(),
            parallel.get(1).totalMs(),
            sequentialAverage,
            parallelAverage,
            sequentialAverage - parallelAverage,
            (sequentialAverage - parallelAverage) * 100.0 / sequentialAverage);
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private void assertPublished(Measurement measurement) {
    assertThat(newsRepository.findById(measurement.newsId()).orElseThrow().isPublished()).isTrue();
  }

  private record Measurement(Long newsId, long totalMs) {}
}
