package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

/** API 응답과 독립적으로 보관하는 분석 당시 자산과 결과. */
public record PortfolioAnalysisSnapshot(
    List<Asset> assets,
    PortfolioImpactSummary summary,
    String overallImpact,
    List<AssetImpact> impacts,
    java.util.Map<String, PortfolioAnalysisPriceContext> priceContexts,
    PortfolioAnalysisPersonalContext personalContext) {

  public PortfolioAnalysisSnapshot(
      List<Asset> assets,
      PortfolioImpactSummary summary,
      String overallImpact,
      List<AssetImpact> impacts,
      java.util.Map<String, PortfolioAnalysisPriceContext> priceContexts) {
    this(assets, summary, overallImpact, impacts, priceContexts, null);
  }

  public PortfolioAnalysisSnapshot(
      List<Asset> assets,
      PortfolioImpactSummary summary,
      String overallImpact,
      List<AssetImpact> impacts) {
    this(assets, summary, overallImpact, impacts, java.util.Map.of());
  }

  public PortfolioAnalysisSnapshot {
    assets = List.copyOf(assets);
    impacts = List.copyOf(impacts);
    priceContexts =
        priceContexts == null ? java.util.Map.of() : java.util.Map.copyOf(priceContexts);
  }

  public boolean hasSameAssets(List<Asset> other) {
    return sameAssets(assets, other);
  }

  public static boolean sameAssets(List<Asset> left, List<Asset> right) {
    return normalized(left).equals(normalized(right));
  }

  private static List<Asset> normalized(List<Asset> assets) {
    return assets.stream()
        .map(
            asset ->
                new Asset(
                    asset.assetName().strip().toLowerCase(Locale.ROOT),
                    asset.weight().stripTrailingZeros(),
                    asset.quantity()))
        .sorted(Comparator.comparing(Asset::assetName))
        .toList();
  }

  public record Asset(String assetName, BigDecimal weight, Long quantity) {
    public Asset(String assetName, BigDecimal weight) {
      this(assetName, weight, null);
    }
  }

  public record AssetImpact(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence,
      int rank,
      String riskSearchStatus,
      boolean noNewNews) {
    public AssetImpact(
        String assetName,
        ImpactDirection direction,
        ImpactLevel impactLevel,
        String issueSummary,
        String expectedReaction,
        String outlook,
        String reason,
        String evidenceSentence,
        int rank,
        String riskSearchStatus) {
      this(
          assetName,
          direction,
          impactLevel,
          issueSummary,
          expectedReaction,
          outlook,
          reason,
          evidenceSentence,
          rank,
          riskSearchStatus,
          false);
    }

    public AssetImpact(
        String assetName,
        ImpactDirection direction,
        ImpactLevel impactLevel,
        String issueSummary,
        String expectedReaction,
        String outlook,
        String reason,
        String evidenceSentence,
        int rank) {
      this(
          assetName,
          direction,
          impactLevel,
          issueSummary,
          expectedReaction,
          outlook,
          reason,
          evidenceSentence,
          rank,
          null);
    }
  }
}
