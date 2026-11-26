package org.grit.daynomy.news.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NewsPublishAiService {

  private final KeywordAiClient keywordAiClient;
  private final MarketAnalysisAiClient marketAnalysisAiClient;
  private final Executor executor;

  public NewsPublishAiService(
      KeywordAiClient keywordAiClient,
      MarketAnalysisAiClient marketAnalysisAiClient,
      @Qualifier("newsPublishAiExecutor") Executor executor) {
    this.keywordAiClient = keywordAiClient;
    this.marketAnalysisAiClient = marketAnalysisAiClient;
    this.executor = executor;
  }

  Analysis analyze(String content) {
    long startedAt = System.nanoTime();
    CompletableFuture<List<NewsKeyword>> keywords;
    CompletableFuture<NewsMarketAnalysis> marketAnalysis;
    try {
      keywords =
          CompletableFuture.supplyAsync(() -> keywordAiClient.extractKeywords(content), executor);
      try {
        marketAnalysis =
            CompletableFuture.supplyAsync(() -> marketAnalysisAiClient.analyze(content), executor);
      } catch (RejectedExecutionException exception) {
        keywords.cancel(true);
        throw exception;
      }
    } catch (RejectedExecutionException exception) {
      logFailure("EXECUTOR_REJECTED", exception, startedAt);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }

    try {
      CompletableFuture.allOf(keywords, marketAnalysis).get();
      return new Analysis(keywords.join(), marketAnalysis.join());
    } catch (InterruptedException exception) {
      keywords.cancel(true);
      marketAnalysis.cancel(true);
      Thread.currentThread().interrupt();
      logFailure("INTERRUPTED", exception, startedAt);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (ExecutionException exception) {
      Throwable failure = exception.getCause() == null ? exception : exception.getCause();
      throw propagate(failure);
    }
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
