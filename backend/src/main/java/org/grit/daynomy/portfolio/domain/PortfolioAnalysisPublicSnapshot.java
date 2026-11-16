package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

/** 공유 링크에 고정 보관하는 공개 가능 포트폴리오 정보. 보유 수량·평가금액·절대 손익은 포함하지 않는다. */
public record PortfolioAnalysisPublicSnapshot(
    int totalAssetCount,
    int analyzedAssetCount,
    boolean allAssetsShared,
    boolean hasAnalysis,
    ImpactDirection overallDirection,
    BigDecimal overallScore,
    BigDecimal positiveImpactScore,
    BigDecimal negativeImpactScore,
    Instant analyzedAt,
    String overallImpact,
    List<AssetImpact> impacts,
    List<Source> sources,
    List<Asset> excludedAssets) {

  public PortfolioAnalysisPublicSnapshot {
    impacts = List.copyOf(impacts);
    sources = List.copyOf(sources);
    excludedAssets = List.copyOf(excludedAssets);
  }

  public record Asset(String assetName, BigDecimal weight) {}

  public record AssetImpact(
      String assetName,
      BigDecimal weight,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence,
      List<Source> sources,
      HoldingContext holding,
      int rank) {

    public AssetImpact {
      sources = List.copyOf(sources);
    }
  }

  public record HoldingContext(
      String assetCode,
      String market,
      LocalDate firstPurchasedOn,
      BigDecimal averagePurchasePrice,
      BigDecimal currentPrice,
      String currentPriceCurrency,
      LocalDate currentPriceBaseDate,
      Instant currentPriceAsOf,
      BigDecimal returnRate) {}

  public record Source(
      String title,
      String url,
      LocalDate publishedOn,
      LocalDate occurredOn,
      String excerpt,
      String factQuote) {}
}
