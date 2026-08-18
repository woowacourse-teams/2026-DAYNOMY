package org.grit.daynomy.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.market.domain.asset.Asset;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.grit.daynomy.market.entity.AssetImpactEntity;
import org.grit.daynomy.market.entity.NewsMarketAnalysisEntity;
import org.grit.daynomy.market.entity.ScenarioEntity;
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
        .isEqualTo(ErrorCode.MARKET_ANALYSIS_NOT_FOUND);
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
