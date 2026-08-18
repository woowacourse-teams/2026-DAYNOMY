package org.grit.daynomy.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.market.domain.analysis.MarketAnalysis;
import org.grit.daynomy.market.domain.asset.Asset;
import org.grit.daynomy.market.domain.asset.AssetImpact;
import org.grit.daynomy.market.domain.asset.Assets;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.Scenario;
import org.grit.daynomy.market.domain.scenario.Scenarios;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.grit.daynomy.market.entity.AssetImpactEntity;
import org.grit.daynomy.market.entity.NewsMarketAnalysisEntity;
import org.grit.daynomy.market.entity.ScenarioEntity;
import org.grit.daynomy.market.exception.MarketErrorCode;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
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
    MarketAnalysis marketAnalysis = createMarketAnalysis();

    marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);

    verify(newsMarketAnalysisRepository)
        .save(
            argThat(
                entity -> {
                  assertThat(entity.getNews()).isSameAs(news);
                  assertThat(entity.getCause()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
                  assertThat(entity.getAssets()).hasSize(1);
                  assertThat(entity.getAssets().get(0).getAsset()).isEqualTo(Asset.STOCK);
                  assertThat(entity.getScenarios()).hasSize(3);
                  assertThat(entity.getScenarios().get(0).getTimeHorizon())
                      .isEqualTo(TimeHorizon.SHORT_TERM);
                  return true;
                }));
  }

  @Test
  @DisplayName("뉴스 ID로 시장 분석을 조회한다")
  void findMarketAnalysisReturnsAnalysis() {
    given(newsMarketAnalysisRepository.findByNewsId(1L))
        .willReturn(Optional.of(createMarketAnalysisEntity()));

    var response = marketAnalysisService.getMarketAnalysis(1L);

    assertThat(response.cause()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
    assertThat(response.assets()).hasSize(1);
    assertThat(response.assets().get(0).asset()).isEqualTo(Asset.STOCK);
    assertThat(response.assets().get(0).direction()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(response.assets().get(0).impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(response.scenarios()).hasSize(1);
    assertThat(response.scenarios().get(0).timeHorizon()).isEqualTo(TimeHorizon.SHORT_TERM);
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

  private NewsMarketAnalysisEntity createMarketAnalysisEntity() {
    return new NewsMarketAnalysisEntity(
        createNews(),
        "금리 인하 기대가 위험자산 선호를 높입니다.",
        List.of(
            new AssetImpactEntity(
                Asset.STOCK,
                ImpactDirection.POSITIVE,
                ImpactLevel.HIGH,
                "할인율 하락 기대가 주식 밸류에이션에 긍정적입니다.")),
        List.of(
            new ScenarioEntity(
                TimeHorizon.SHORT_TERM,
                "단기적으로 주식 선호가 개선될 수 있습니다.",
                70,
                "금리 인하 기대가 투자 심리를 자극하기 때문입니다.")));
  }

  private MarketAnalysis createMarketAnalysis() {
    return new MarketAnalysis(
        "금리 인하 기대가 위험자산 선호를 높입니다.",
        new Assets(
            List.of(
                new AssetImpact(
                    Asset.STOCK,
                    ImpactDirection.POSITIVE,
                    ImpactLevel.HIGH,
                    "할인율 하락 기대가 주식 밸류에이션에 긍정적입니다."))),
        new Scenarios(
            List.of(
                new Scenario(
                    TimeHorizon.SHORT_TERM,
                    "단기적으로 주식 선호가 개선될 수 있습니다.",
                    70,
                    "금리 인하 기대가 투자 심리를 자극하기 때문입니다."),
                new Scenario(
                    TimeHorizon.MID_TERM,
                    "중기적으로 정책 강도에 따라 자산별 차별화가 나타날 수 있습니다.",
                    55,
                    "실제 정책 집행 속도에 불확실성이 있기 때문입니다."),
                new Scenario(
                    TimeHorizon.LONG_TERM,
                    "장기적으로 경기 흐름이 자산 가격을 좌우할 수 있습니다.",
                    45,
                    "뉴스 본문만으로 장기 경로를 단정하기 어렵기 때문입니다."))));
  }

  private News createNews() {
    return new News(
        "market news",
        "content",
        "description",
        "image.png",
        Category.STOCK,
        LocalDateTime.of(2026, 8, 17, 10, 0));
  }
}
