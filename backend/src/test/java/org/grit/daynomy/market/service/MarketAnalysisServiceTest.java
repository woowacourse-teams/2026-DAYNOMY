package org.grit.daynomy.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.exception.MarketErrorCode;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
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
                  assertThat(entity.getObservedFact()).isEqualTo("기준금리가 인하됐습니다.");
                  assertThat(entity.getInterpretation()).isEqualTo("위험자산 선호가 높아질 수 있습니다.");
                  assertThat(entity.getRisk()).isEqualTo("물가가 다시 상승하면 해석이 달라질 수 있습니다.");
                  assertThat(entity.getWatchPoint()).isEqualTo("다음 소비자물가 발표를 확인해야 합니다.");
                  assertThat(entity.getLimitation()).isEqualTo("자산별 가격 데이터는 포함하지 않았습니다.");
                  return true;
                }));
  }

  @Test
  @DisplayName("기존 시장 분석을 새로운 분석 내용으로 수정한다")
  void updateMarketAnalysisChangesAnalysis() {
    NewsMarketAnalysis savedMarketAnalysis = createSavedMarketAnalysis();
    NewsMarketAnalysis newMarketAnalysis = createUpdatedMarketAnalysis();
    given(newsMarketAnalysisRepository.findByNewsId(1L))
        .willReturn(Optional.of(savedMarketAnalysis));

    marketAnalysisService.updateMarketAnalysis(1L, newMarketAnalysis);

    assertThat(savedMarketAnalysis.getObservedFact()).isEqualTo("수정된 사실");
    assertThat(savedMarketAnalysis.getInterpretation()).isEqualTo("수정된 해석");
    assertThat(savedMarketAnalysis.getRisk()).isEqualTo("수정된 위험");
    assertThat(savedMarketAnalysis.getWatchPoint()).isEqualTo("수정된 확인 지표");
    assertThat(savedMarketAnalysis.getLimitation()).isEqualTo("수정된 분석 한계");
  }

  @Test
  @DisplayName("수정할 시장 분석이 없으면 예외를 던진다")
  void updateMarketAnalysisThrowsWhenMissing() {
    given(newsMarketAnalysisRepository.findByNewsId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> marketAnalysisService.updateMarketAnalysis(1L, createUpdatedMarketAnalysis()))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MarketErrorCode.MARKET_ANALYSIS_NOT_FOUND);
  }

  @Test
  @DisplayName("뉴스 ID로 시장 분석을 조회한다")
  void findMarketAnalysisReturnsAnalysis() {
    given(newsMarketAnalysisRepository.findByNewsId(1L))
        .willReturn(Optional.of(createSavedMarketAnalysis()));

    var response = marketAnalysisService.getMarketAnalysis(1L);

    assertThat(response.observedFact()).isEqualTo("기준금리가 인하됐습니다.");
    assertThat(response.interpretation()).isEqualTo("위험자산 선호가 높아질 수 있습니다.");
    assertThat(response.risk()).isEqualTo("물가가 다시 상승하면 해석이 달라질 수 있습니다.");
    assertThat(response.watchPoint()).isEqualTo("다음 소비자물가 발표를 확인해야 합니다.");
    assertThat(response.limitation()).isEqualTo("자산별 가격 데이터는 포함하지 않았습니다.");
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
            "image.png",
            List.of(new NewsSourceInfo("DART", "https://example.com/draft-news")),
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
    return new NewsMarketAnalysis(
        news,
        "기준금리가 인하됐습니다.",
        "위험자산 선호가 높아질 수 있습니다.",
        "물가가 다시 상승하면 해석이 달라질 수 있습니다.",
        "다음 소비자물가 발표를 확인해야 합니다.",
        "자산별 가격 데이터는 포함하지 않았습니다.");
  }

  private NewsMarketAnalysis createMarketAnalysis() {
    return new NewsMarketAnalysis(
        "기준금리가 인하됐습니다.",
        "위험자산 선호가 높아질 수 있습니다.",
        "물가가 다시 상승하면 해석이 달라질 수 있습니다.",
        "다음 소비자물가 발표를 확인해야 합니다.",
        "자산별 가격 데이터는 포함하지 않았습니다.");
  }

  private NewsMarketAnalysis createUpdatedMarketAnalysis() {
    return new NewsMarketAnalysis("수정된 사실", "수정된 해석", "수정된 위험", "수정된 확인 지표", "수정된 분석 한계");
  }

  private News createNews() {
    return News.createPublished(
        "market news",
        "content",
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/market-news")),
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }
}
