package org.grit.daynomy.news.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.ExternalTransportFailure;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.openai.OpenAiHttpFailure;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class NewsPublishAiService {

  private static final int MAX_ATTEMPTS = 2;

  private final KeywordAiClient keywordAiClient;
  private final MarketAnalysisAiClient marketAnalysisAiClient;
  private final Executor executor;
  private final long timeoutMillis;
  private final Duration retryDelay;

  public NewsPublishAiService(
      KeywordAiClient keywordAiClient,
      MarketAnalysisAiClient marketAnalysisAiClient,
      @Qualifier("newsPublishAiExecutor") Executor executor,
      @Value("${news.publish.ai.timeout:120s}") Duration timeout,
      @Value("${news.publish.ai.retry-delay:250ms}") Duration retryDelay) {
    if (timeout == null
        || timeout.toMillis() < 1
        || retryDelay == null
        || retryDelay.isNegative()
        || retryDelay.compareTo(timeout) >= 0) {
      throw new IllegalArgumentException(
          "News publish AI timeout must be positive and greater than retry delay.");
    }
    this.keywordAiClient = keywordAiClient;
    this.marketAnalysisAiClient = marketAnalysisAiClient;
    this.executor = executor;
    this.timeoutMillis = timeout.toMillis();
    this.retryDelay = retryDelay;
  }

  Analysis analyze(String content) {
    long startedAt = System.nanoTime();
    CompletableFuture<List<NewsKeyword>> keywords;
    CompletableFuture<NewsMarketAnalysis> marketAnalysis;
    try {
      keywords =
          CompletableFuture.supplyAsync(
              () -> retry(() -> keywordAiClient.extractKeywords(content)), executor);
      try {
        marketAnalysis =
            CompletableFuture.supplyAsync(
                () -> retry(() -> marketAnalysisAiClient.analyze(content)), executor);
      } catch (RejectedExecutionException exception) {
        keywords.cancel(true);
        throw exception;
      }
    } catch (RejectedExecutionException exception) {
      logFailure("EXECUTOR_REJECTED", exception, startedAt);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }

    CompletableFuture<Analysis> combined = failFast(keywords, marketAnalysis);
    try {
      return combined.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      cancelAll(combined, keywords, marketAnalysis);
      Thread.currentThread().interrupt();
      logFailure("INTERRUPTED", exception, startedAt);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (TimeoutException exception) {
      cancelAll(combined, keywords, marketAnalysis);
      logFailure("TIMEOUT", exception, startedAt);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (ExecutionException exception) {
      Throwable failure = exception.getCause() == null ? exception : exception.getCause();
      throw propagate(failure);
    }
  }

  private <T> T retry(Supplier<T> operation) {
    for (int attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (RuntimeException exception) {
        if (attempt >= MAX_ATTEMPTS || !isRetryable(exception)) {
          throw exception;
        }
        waitBeforeRetry();
      }
    }
  }

  private boolean isRetryable(RuntimeException exception) {
    if (exception instanceof HttpStatusCodeException httpFailure) {
      return OpenAiHttpFailure.from(httpFailure).retryable();
    }
    if (exception instanceof RestClientException transportFailure) {
      return ExternalTransportFailure.from(transportFailure).retryable();
    }
    return false;
  }

  private void waitBeforeRetry() {
    try {
      Thread.sleep(retryDelay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private CompletableFuture<Analysis> failFast(
      CompletableFuture<List<NewsKeyword>> keywords,
      CompletableFuture<NewsMarketAnalysis> marketAnalysis) {
    var combined = new CompletableFuture<Analysis>();
    keywords.whenComplete(
        (result, failure) -> {
          if (failure != null) {
            if (combined.completeExceptionally(unwrap(failure))) {
              marketAnalysis.cancel(true);
            }
          } else if (marketAnalysis.isDone() && !marketAnalysis.isCompletedExceptionally()) {
            combined.complete(new Analysis(result, marketAnalysis.join()));
          }
        });
    marketAnalysis.whenComplete(
        (result, failure) -> {
          if (failure != null) {
            if (combined.completeExceptionally(unwrap(failure))) {
              keywords.cancel(true);
            }
          } else if (keywords.isDone() && !keywords.isCompletedExceptionally()) {
            combined.complete(new Analysis(keywords.join(), result));
          }
        });
    return combined;
  }

  private Throwable unwrap(Throwable failure) {
    if (failure instanceof CompletionException && failure.getCause() != null) {
      return failure.getCause();
    }
    return failure;
  }

  private void cancelAll(
      CompletableFuture<Analysis> combined,
      CompletableFuture<List<NewsKeyword>> keywords,
      CompletableFuture<NewsMarketAnalysis> marketAnalysis) {
    combined.cancel(true);
    keywords.cancel(true);
    marketAnalysis.cancel(true);
  }

  private void logFailure(String reasonCode, Throwable cause, long startedAt) {
    var event =
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("operation", "news-publish-analysis")
            .addKeyValue("reasonCode", reasonCode)
            .addKeyValue("errorType", cause.getClass().getSimpleName())
            .addKeyValue(
                "durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    if (cause instanceof BusinessException business) {
      event.addKeyValue("errorCode", business.errorCode().code());
    }
    event.log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
  }

  private RuntimeException propagate(Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (cause instanceof Error error) {
      throw error;
    }
    return new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }

  record Analysis(List<NewsKeyword> keywords, NewsMarketAnalysis marketAnalysis) {}
}
