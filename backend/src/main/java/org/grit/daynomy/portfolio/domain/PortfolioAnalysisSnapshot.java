package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

/** API 응답과 독립적으로 보관하는 분석 당시 자산과 결과. */
public record PortfolioAnalysisSnapshot(
    List<Asset> assets,
    PortfolioImpactSummary summary,
    String overallImpact,
    List<AssetImpact> impacts) {

  public PortfolioAnalysisSnapshot {
    assets = List.copyOf(assets);
    impacts = List.copyOf(impacts);
  }

  public record Asset(String assetName, BigDecimal weight) {}

  public record AssetImpact(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence,
      int rank) {}
}
