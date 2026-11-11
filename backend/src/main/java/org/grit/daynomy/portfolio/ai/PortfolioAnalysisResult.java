package org.grit.daynomy.portfolio.ai;

import java.time.LocalDate;
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
      List<Source> sources,
      int sortOrder,
      String riskSearchStatus,
      boolean noNewNews) {
    public AssetImpactResult(
        String assetName,
        ImpactDirection direction,
        ImpactLevel impactLevel,
        String issueSummary,
        String expectedReaction,
        String outlook,
        String reason,
        String evidenceSentence,
        List<Source> sources,
        int sortOrder,
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
          sources,
          sortOrder,
          riskSearchStatus,
          false);
    }

    public AssetImpactResult(
        String assetName,
        ImpactDirection direction,
        ImpactLevel impactLevel,
        String issueSummary,
        String expectedReaction,
        String outlook,
        String reason,
        String evidenceSentence,
        List<Source> sources,
        int sortOrder) {
      this(
          assetName,
          direction,
          impactLevel,
          issueSummary,
          expectedReaction,
          outlook,
          reason,
          evidenceSentence,
          sources,
          sortOrder,
          null);
    }

    public AssetImpactResult withRiskSearchStatus(String status) {
      return new AssetImpactResult(
          assetName,
          direction,
          impactLevel,
          issueSummary,
          expectedReaction,
          outlook,
          reason,
          evidenceSentence,
          sources,
          sortOrder,
          status,
          noNewNews);
    }

    public AssetImpactResult {
      sources = List.copyOf(sources);
    }
  }

  public record Source(
      String title,
      String url,
      LocalDate publishedOn,
      LocalDate occurredOn,
      String excerpt,
      String factQuote) {
    public Source(
        String title, String url, LocalDate publishedOn, LocalDate occurredOn, String excerpt) {
      this(title, url, publishedOn, occurredOn, excerpt, null);
    }

    public Source(String title, String url) {
      this(title, url, null, null, "");
    }
  }
}
