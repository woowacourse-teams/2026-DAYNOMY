package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisSourceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PortfolioAnalysisShareSnapshotFactory {

  private final PortfolioAnalysisEvidenceRepository evidence;

  public PortfolioAnalysisPublicSnapshot fromAnalysis(
      Long memberId,
      PortfolioAnalysis analysis,
      Map<String, PortfolioAnalysisPublicSnapshot.HoldingContext> holdingContexts,
      List<String> requestedAssetNames) {
    PortfolioAnalysisResponse response =
        PortfolioAnalysisResponse.from(
            analysis,
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                analysis.getId(), memberId));
    LinkedHashSet<String> assetNames = normalizedAssetNames(requestedAssetNames);
    var selectedImpacts =
        response.impacts().stream()
            .filter(impact -> assetNames.contains(impact.assetName()))
            .toList();
    if (assetNames.size() != requestedAssetNames.size()
        || selectedImpacts.size() != assetNames.size()) {
      throw invalidAssets();
    }
    boolean allAssetsShared =
        selectedImpacts.size() == response.impacts().size() && response.excludedAssets().isEmpty();
    return new PortfolioAnalysisPublicSnapshot(
        selectedImpacts.size(),
        selectedImpacts.size(),
        allAssetsShared,
        true,
        allAssetsShared ? response.overallDirection() : null,
        allAssetsShared ? response.overallScore() : null,
        allAssetsShared ? response.positiveImpactScore() : BigDecimal.ZERO,
        allAssetsShared ? response.negativeImpactScore() : BigDecimal.ZERO,
        response.analyzedAt(),
        allAssetsShared ? response.overallImpact() : "",
        selectedImpacts.stream()
            .map(
                impact ->
                    new PortfolioAnalysisPublicSnapshot.AssetImpact(
                        impact.assetName(),
                        impact.weight(),
                        impact.direction(),
                        impact.impactLevel(),
                        impact.issueSummary(),
                        impact.expectedReaction(),
                        impact.outlook(),
                        impact.reason(),
                        impact.evidenceSentence(),
                        impact.sources().stream().map(this::source).toList(),
                        holdingContexts.get(impact.assetName()),
                        impact.rank()))
            .toList(),
        selectedImpacts.stream()
            .flatMap(impact -> impact.sources().stream())
            .distinct()
            .map(this::source)
            .toList(),
        List.of());
  }

  public PortfolioAnalysisPublicSnapshot fromPortfolio(
      List<PortfolioHoldingResponse> holdings,
      Map<String, PortfolioAnalysisPublicSnapshot.HoldingContext> holdingContexts,
      List<String> requestedAssetNames) {
    LinkedHashSet<String> assetNames = normalizedAssetNames(requestedAssetNames);
    var available =
        holdings.stream()
            .filter(holding -> holding.priceAvailable() && holding.weight() != null)
            .toList();
    var selected =
        available.stream().filter(holding -> assetNames.contains(holding.name())).toList();
    if (assetNames.size() != requestedAssetNames.size() || selected.size() != assetNames.size()) {
      throw invalidAssets();
    }
    var impacts =
        IntStream.range(0, selected.size())
            .mapToObj(
                index -> {
                  var holding = selected.get(index);
                  return new PortfolioAnalysisPublicSnapshot.AssetImpact(
                      holding.name(),
                      holding.weight(),
                      null,
                      null,
                      "",
                      "",
                      "",
                      "",
                      "",
                      List.of(),
                      holdingContexts.get(holding.name()),
                      index + 1);
                })
            .toList();
    return new PortfolioAnalysisPublicSnapshot(
        impacts.size(),
        0,
        impacts.size() == holdings.size(),
        false,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        "",
        impacts,
        List.of(),
        List.of());
  }

  private PortfolioAnalysisPublicSnapshot.Source source(PortfolioAnalysisSourceResponse source) {
    return new PortfolioAnalysisPublicSnapshot.Source(
        source.title(),
        source.url(),
        source.publishedOn(),
        source.occurredOn(),
        source.excerpt(),
        source.factQuote());
  }

  private LinkedHashSet<String> normalizedAssetNames(List<String> requestedAssetNames) {
    LinkedHashSet<String> assetNames = new LinkedHashSet<>();
    requestedAssetNames.forEach(assetName -> assetNames.add(assetName.strip()));
    return assetNames;
  }

  private BusinessException invalidAssets() {
    return new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
  }
}
