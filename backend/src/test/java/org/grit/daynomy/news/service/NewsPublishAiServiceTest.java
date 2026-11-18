package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsPublishAiServiceTest {

  @Mock private KeywordAiClient keywordAiClient;
  @Mock private MarketAnalysisAiClient marketAnalysisAiClient;

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
      var service = new NewsPublishAiService(keywordAiClient, marketAnalysisAiClient, executor);

      NewsPublishAiService.Analysis result = service.analyze("뉴스 본문");

      assertThat(result.keywords()).isEqualTo(keywords);
      assertThat(result.marketAnalysis()).isSameAs(marketAnalysis);
    }
  }

  @Test
  void aiFailureIsPropagatedAfterBothCallsFinish() {
    RuntimeException failure = new RuntimeException("keyword failed");
    given(keywordAiClient.extractKeywords("뉴스 본문")).willThrow(failure);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(new NewsMarketAnalysis("시장 분석"));

    try (var executor = Executors.newFixedThreadPool(2)) {
      var service = new NewsPublishAiService(keywordAiClient, marketAnalysisAiClient, executor);

      assertThatThrownBy(() -> service.analyze("뉴스 본문")).isSameAs(failure);
    }

    verify(marketAnalysisAiClient).analyze("뉴스 본문");
  }

  private void awaitBoth(CountDownLatch started) throws InterruptedException {
    started.countDown();
    assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
  }
}
