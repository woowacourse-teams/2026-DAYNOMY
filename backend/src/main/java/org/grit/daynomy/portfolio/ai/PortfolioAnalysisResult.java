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
      int sortOrder) {}

  public record Source(String title, String url) {}
}
