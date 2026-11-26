package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class NewsPublishAiServiceTest {

  @Mock private KeywordAiClient keywordAiClient;
  @Mock private MarketAnalysisAiClient marketAnalysisAiClient;

  private final Logger logger = (Logger) LoggerFactory.getLogger(NewsPublishAiService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUpLogging() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void keywordAndMarketAnalysisStartConcurrently() {
    CountDownLatch started = new CountDownLatch(2);
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리", "정의", "맥락", "관찰점"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("시장 분석");
    given(keywordAiClient.extractKeywords("뉴스 본문"))
        .willAnswer(
            invocation -> {
              awaitBoth(started);
              return keywords;
            });
    given(marketAnalysisAiClient.analyze("뉴스 본문"))
        .willAnswer(
            invocation -> {
              awaitBoth(started);
              return marketAnalysis;
            });

    try (var executor = Executors.newFixedThreadPool(2)) {
      var service = service(executor);

      NewsPublishAiService.Analysis result = service.analyze("뉴스 본문");

      assertThat(result.keywords()).isEqualTo(keywords);
      assertThat(result.marketAnalysis()).isSameAs(marketAnalysis);
    }
  }

  @Test
  void aiFailureCancelsOtherTaskAndIsPropagatedWithoutDuplicateLogging() throws Exception {
    RuntimeException failure = new RuntimeException("keyword failed");
    CountDownLatch marketStarted = new CountDownLatch(1);
    CountDownLatch releaseMarket = new CountDownLatch(1);
    given(keywordAiClient.extractKeywords("뉴스 본문"))
        .willAnswer(
            invocation -> {
              assertThat(marketStarted.await(1, TimeUnit.SECONDS)).isTrue();
              throw failure;
            });
    given(marketAnalysisAiClient.analyze("뉴스 본문"))
        .willAnswer(
            invocation -> {
              marketStarted.countDown();
              assertThat(releaseMarket.await(1, TimeUnit.SECONDS)).isTrue();
              return new NewsMarketAnalysis("시장 분석");
            });

    try (var executor = Executors.newFixedThreadPool(2)) {
      var service = service(executor);
      CompletableFuture<Throwable> result =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  service.analyze("뉴스 본문");
                  return null;
                } catch (Throwable throwable) {
                  return throwable;
                }
              });

      try {
        assertThat(marketStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get(1, TimeUnit.SECONDS)).isSameAs(failure);
      } finally {
        releaseMarket.countDown();
      }
    }

    verify(marketAnalysisAiClient).analyze("뉴스 본문");
    assertThat(appender.list)
        .noneMatch(
            loggingEvent ->
                LogEvent.AI_NEWS_GENERATION_FAILED
                    .code()
                    .equals(keyValues(loggingEvent).get("event")));
  }

  @Test
  void retriesRetryableFailureOnceAndReturnsSuccessfulResult() {
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리", "정의", "맥락", "관찰점"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("시장 분석");
    var failure =
        HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE,
            "unavailable",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);
    given(keywordAiClient.extractKeywords("뉴스 본문")).willThrow(failure).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(marketAnalysis);

    NewsPublishAiService.Analysis result = service(Runnable::run).analyze("뉴스 본문");

    assertThat(result.keywords()).isEqualTo(keywords);
    assertThat(result.marketAnalysis()).isSameAs(marketAnalysis);
    verify(keywordAiClient, times(2)).extractKeywords("뉴스 본문");
    verify(marketAnalysisAiClient).analyze("뉴스 본문");
  }

  @Test
  void retryableFailureIsPropagatedAfterRetryIsExhausted() {
    var failure =
        HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE,
            "unavailable",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);
    given(keywordAiClient.extractKeywords("뉴스 본문")).willThrow(failure);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(new NewsMarketAnalysis("시장 분석"));

    assertThatThrownBy(() -> service(Runnable::run).analyze("뉴스 본문")).isSameAs(failure);

    verify(keywordAiClient, times(2)).extractKeywords("뉴스 본문");
  }

  @Test
  void doesNotRetryPermanentFailure() {
    var failure =
        HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);
    given(keywordAiClient.extractKeywords("뉴스 본문")).willThrow(failure);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(new NewsMarketAnalysis("시장 분석"));

    assertThatThrownBy(() -> service(Runnable::run).analyze("뉴스 본문")).isSameAs(failure);

    verify(keywordAiClient).extractKeywords("뉴스 본문");
  }

  @Test
  void timeoutCancelsAiTasksAndReturnsAiFailure() {
    CountDownLatch release = new CountDownLatch(1);
    given(keywordAiClient.extractKeywords("뉴스 본문"))
        .willAnswer(
            invocation -> {
              release.await(1, TimeUnit.SECONDS);
              return List.of();
            });
    given(marketAnalysisAiClient.analyze("뉴스 본문"))
        .willAnswer(
            invocation -> {
              release.await(1, TimeUnit.SECONDS);
              return new NewsMarketAnalysis("시장 분석");
            });

    try (var executor = Executors.newFixedThreadPool(2)) {
      var service = service(executor, Duration.ofMillis(50));
      try {
        assertThatThrownBy(() -> service.analyze("뉴스 본문"))
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                    assertThat(exception.errorCode())
                        .isEqualTo(ExternalErrorCode.AI_NEWS_GENERATION_FAILED));
      } finally {
        release.countDown();
      }
    }

    ILoggingEvent log = logEvent(LogEvent.AI_NEWS_GENERATION_FAILED);
    assertThat(keyValues(log))
        .containsEntry("operation", "news-publish-analysis")
        .containsEntry("reasonCode", "TIMEOUT")
        .containsEntry("errorType", TimeoutException.class.getSimpleName())
        .containsKey("durationMs");
  }

  @Test
  void secondTaskRejectionCancelsFirstTaskAndReturnsAiFailure() {
    AtomicInteger submissions = new AtomicInteger();
    AtomicReference<Runnable> accepted = new AtomicReference<>();
    Executor executor =
        task -> {
          if (submissions.getAndIncrement() == 0) {
            accepted.set(task);
            return;
          }
          throw new RejectedExecutionException("queue full");
        };
    var service = service(executor);

    assertThatThrownBy(() -> service.analyze("뉴스 본문"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.AI_NEWS_GENERATION_FAILED));

    accepted.get().run();
    verifyNoInteractions(keywordAiClient, marketAnalysisAiClient);

    ILoggingEvent log = logEvent(LogEvent.AI_NEWS_GENERATION_FAILED);
    assertThat(keyValues(log))
        .containsEntry("operation", "news-publish-analysis")
        .containsEntry("reasonCode", "EXECUTOR_REJECTED")
        .containsEntry("errorType", RejectedExecutionException.class.getSimpleName())
        .containsKey("durationMs");
  }

  private NewsPublishAiService service(Executor executor) {
    return service(executor, Duration.ofSeconds(1));
  }

  private NewsPublishAiService service(Executor executor, Duration timeout) {
    return new NewsPublishAiService(
        keywordAiClient, marketAnalysisAiClient, executor, timeout, Duration.ZERO);
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private void awaitBoth(CountDownLatch started) throws InterruptedException {
    started.countDown();
    assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
  }
}
