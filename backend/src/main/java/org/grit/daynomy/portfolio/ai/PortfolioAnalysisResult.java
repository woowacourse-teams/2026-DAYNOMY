package org.grit.daynomy.portfolio.ai;

import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

public record PortfolioAnalysisResult(List<AssetImpactResult> impacts) {

  public PortfolioAnalysisResult {
    impacts = List.copyOf(impacts);
  }

  public record AssetImpactResult(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String expectedReaction,
      String reason,
      String evidenceSentence,
      int sortOrder) {}
}
