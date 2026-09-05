package org.grit.daynomy.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.exception.MarketErrorCode;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketAnalysisServiceTest {

  @Mock private NewsMarketAnalysisRepository newsMarketAnalysisRepository;

  @InjectMocks private MarketAnalysisService marketAnalysisService;

  @Test
  @DisplayName("뉴스와 시장 분석을 받아 시장 분석을 저장한다")
  void saveMarketAnalysisStoresNewsMarketAnalysis() {
    News news = createNews();
    NewsMarketAnalysis marketAnalysis = createMarketAnalysis();

    marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);

    verify(newsMarketAnalysisRepository)
        .save(
            argThat(
                entity -> {
                  assertThat(entity.getNews()).isSameAs(news);
                  assertThat(entity.getSummary())
                      .isEqualTo("금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.");
                  return true;
                }));
  }

  @Test
  @DisplayName("뉴스 ID로 시장 분석을 조회한다")
  void findMarketAnalysisReturnsAnalysis() {
    given(newsMarketAnalysisRepository.findByNewsId(1L))
        .willReturn(Optional.of(createSavedMarketAnalysis()));

    var response = marketAnalysisService.getMarketAnalysis(1L);

    assertThat(response.summary())
        .isEqualTo("금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.");
  }

  @Test
  @DisplayName("시장 분석이 없으면 예외를 던진다")
  void findMarketAnalysisThrowsWhenMissing() {
    given(newsMarketAnalysisRepository.findByNewsId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> marketAnalysisService.getMarketAnalysis(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MarketErrorCode.MARKET_ANALYSIS_NOT_FOUND);
  }

  @Test
  @DisplayName("비발행 뉴스의 시장 분석은 조회하지 않는다")
  void findMarketAnalysisThrowsWhenNewsIsNotPublished() {
    News draft =
        News.createDraft(
            "draft news",
            "content",
            "description",
            "image.png",
            NewsSource.DART,
            "draft-news",
            "https://example.com/draft-news",
            Category.STOCK);
    given(newsMarketAnalysisRepository.findByNewsId(1L))
        .willReturn(Optional.of(createSavedMarketAnalysis(draft)));

    assertThatThrownBy(() -> marketAnalysisService.getMarketAnalysis(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MarketErrorCode.MARKET_ANALYSIS_NOT_FOUND);
  }

  private NewsMarketAnalysis createSavedMarketAnalysis() {
    return createSavedMarketAnalysis(createNews());
  }

  private NewsMarketAnalysis createSavedMarketAnalysis(News news) {
    return new NewsMarketAnalysis(news, "금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.");
  }

  private NewsMarketAnalysis createMarketAnalysis() {
    return new NewsMarketAnalysis("금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.");
  }

  private News createNews() {
    return News.createPublished(
        "market news",
        "content",
        "description",
        "image.png",
        NewsSource.DART,
        "market-news",
        "https://example.com/market-news",
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }
}
