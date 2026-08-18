package org.grit.daynomy.market.parser.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.market.domain.analysis.MarketAnalysis;
import org.grit.daynomy.market.domain.asset.Asset;
import org.grit.daynomy.market.domain.asset.AssetImpact;
import org.grit.daynomy.market.domain.asset.Assets;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.Scenario;
import org.grit.daynomy.market.domain.scenario.Scenarios;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketAnalysisParserTest {

  @Test
  @DisplayName("시장 분석 도메인을 엔티티로 변환한다")
  void parseDomainToEntity() {
    News news = createNews();
    MarketAnalysis domain = createMarketAnalysis();

    var entity = MarketAnalysisParser.toEntity(news, domain);

    assertThat(entity.getNews()).isSameAs(news);
    assertThat(entity.getCause()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
    assertThat(entity.getAssets()).hasSize(2);
    assertThat(entity.getAssets().get(0).getAsset()).isEqualTo(Asset.STOCK);
    assertThat(entity.getAssets().get(0).getDirection()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(entity.getAssets().get(0).getImpactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(entity.getScenarios()).hasSize(1);
    assertThat(entity.getScenarios().get(0).getTimeHorizon()).isEqualTo(TimeHorizon.SHORT_TERM);
    assertThat(entity.getScenarios().get(0).getProbability()).isEqualTo(70);
  }

  @Test
  @DisplayName("시장 분석 엔티티를 도메인으로 변환한다")
  void parseEntityToDomain() {
    var entity = MarketAnalysisParser.toEntity(createNews(), createMarketAnalysis());

    MarketAnalysis domain = MarketAnalysisParser.toDomain(entity);

    assertThat(domain.getCause()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
    assertThat(domain.getAssets().getValues()).hasSize(2);
    assertThat(domain.getAssets().getValues().get(1).getAsset()).isEqualTo(Asset.GOLD);
    assertThat(domain.getAssets().getValues().get(1).getImpactLevel())
        .isEqualTo(ImpactLevel.MEDIUM);
    assertThat(domain.getScenarios().getValues()).hasSize(1);
    assertThat(domain.getScenarios().getValues().get(0).getPrediction())
        .isEqualTo("단기적으로 주식 선호가 개선될 수 있습니다.");
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
                    "할인율 하락 기대가 주식 밸류에이션에 긍정적입니다."),
                new AssetImpact(
                    Asset.GOLD,
                    ImpactDirection.POSITIVE,
                    ImpactLevel.MEDIUM,
                    "실질금리 하락 기대가 금 가격에 우호적입니다."))),
        new Scenarios(
            List.of(
                new Scenario(
                    TimeHorizon.SHORT_TERM,
                    "단기적으로 주식 선호가 개선될 수 있습니다.",
                    70,
                    "금리 인하 기대가 투자 심리를 자극하기 때문입니다."))));
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
