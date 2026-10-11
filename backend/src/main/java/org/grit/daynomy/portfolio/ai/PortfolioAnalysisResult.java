package org.grit.daynomy.portfolio.ai;

import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

public record PortfolioAnalysisResult(
    String overallImpact, List<AssetImpactResult> impacts, List<Source> sources) {

  public PortfolioAnalysisResult {
    impacts = List.copyOf(impacts);
    sources = List.copyOf(sources);
  }

  public record AssetImpactResult(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence,
      String marketOpinion,
      List<String> reasoningSteps,
      String portfolioImpact,
      String counterScenario,
      String nextIndicator,
      List<Source> sources,
      int sortOrder) {

    public AssetImpactResult {
      reasoningSteps = List.copyOf(reasoningSteps);
      sources = List.copyOf(sources);
    }
  }

  public record Source(String title, String url) {}
}
