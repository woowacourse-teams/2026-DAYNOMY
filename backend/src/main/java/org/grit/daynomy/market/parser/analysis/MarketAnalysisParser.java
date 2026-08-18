package org.grit.daynomy.market.parser.analysis;

import org.grit.daynomy.market.domain.analysis.MarketAnalysis;
import org.grit.daynomy.market.entity.NewsMarketAnalysisEntity;
import org.grit.daynomy.market.parser.asset.AssetImpactParser;
import org.grit.daynomy.market.parser.scenario.ScenarioParser;
import org.grit.daynomy.news.domain.News;

public final class MarketAnalysisParser {

  private MarketAnalysisParser() {}

  public static MarketAnalysis toDomain(NewsMarketAnalysisEntity entity) {
    return new MarketAnalysis(
        entity.getCause(),
        AssetImpactParser.toDomain(entity.getAssets()),
        ScenarioParser.toDomain(entity.getScenarios()));
  }

  public static NewsMarketAnalysisEntity toEntity(News news, MarketAnalysis domain) {
    return new NewsMarketAnalysisEntity(
        news,
        domain.getCause(),
        AssetImpactParser.toEntity(domain.getAssets()),
        ScenarioParser.toEntity(domain.getScenarios()));
  }
}
