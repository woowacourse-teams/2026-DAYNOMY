package org.grit.daynomy.portfolio.v2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.*;
import org.grit.daynomy.portfolio.dto.*;
import org.grit.daynomy.portfolio.service.*;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisServiceV2Test {
  private final PortfolioAnalysisAiClient ai = mock(PortfolioAnalysisAiClient.class);
  private final PortfolioAssetAnalysisService assets = mock(PortfolioAssetAnalysisService.class);
  private final PortfolioAnalysisNarrative narrative = mock(PortfolioAnalysisNarrative.class);
  private final PortfolioAnalysisServiceV2 service =
      new PortfolioAnalysisServiceV2(ai, assets, narrative, new GuestPortfolioAnalysisTrial());

  @Test
  void selectsFiveOfFiftyAndPreservesExcludedHoldingsAndOriginalWeights() {
    var holdings =
        IntStream.range(0, 50).mapToObj(i -> asset(String.format("주식%02d", i), "2")).toList();
    when(assets.cachedImpactLevels(anyList())).thenReturn(Map.of("주식49", ImpactLevel.HIGH));
    when(assets.analyze(anyList())).thenAnswer(i -> result(i.getArgument(0)));
    when(narrative.summarize(any())).thenReturn("선별한 종목만 분석했어요.");

    var response = service.analyze(new PortfolioAnalysisRequest(holdings), true);

    assertThat(response.impacts())
        .extracting(PortfolioAssetImpactResponse::assetName)
        .containsExactly("주식00", "주식01", "주식02", "주식49", "주식03");
    assertThat(response.totalAssetCount()).isEqualTo(50);
    assertThat(response.selectedAssetCount()).isEqualTo(5);
    assertThat(response.analyzedAssetCount()).isEqualTo(5);
    assertThat(response.excludedAssets()).hasSize(45);
    assertThat(response.impacts())
        .allSatisfy(i -> assertThat(i.weight()).isEqualByComparingTo("2"));
    assertThat(response.analyzedWeight()).isEqualByComparingTo("10");
    assertThat(response.coverage()).isEqualTo("PARTIAL");
    assertThat(response.overallScore()).isNull();
    assertThat(response.overallDirection()).isNull();
    verifyNoInteractions(ai);
    verify(narrative).summarize(argThat(a -> a.excludedAssets().size() == 45));
  }

  @Test
  void emptyCacheUsesLargestWeightsAndFewerThanFiveAreAllAnalyzed() {
    var holdings =
        List.of(
            asset("A", "30"),
            asset("B", "25"),
            asset("C", "20"),
            asset("D", "15"),
            asset("E", "9"),
            asset("F", "1"));
    assertThat(PortfolioAnalysisSelection.select(holdings, Map.of()))
        .extracting(PortfolioAssetRequest::assetName)
        .containsExactly("A", "B", "C", "D", "E");
    assertThat(PortfolioAnalysisSelection.select(holdings, Map.of("F", ImpactLevel.HIGH)))
        .extracting(PortfolioAssetRequest::assetName)
        .containsExactlyInAnyOrder("A", "B", "C", "F", "D");
    assertThat(PortfolioAnalysisSelection.select(holdings.subList(0, 2), Map.of()))
        .containsExactlyElementsOf(holdings.subList(0, 2));
    assertThat(PortfolioAnalysisSelection.select(List.of(), Map.of())).isEmpty();
  }

  @Test
  void highImpactCandidatesDoNotDisplaceTopThreeOrExceedFive() {
    var holdings =
        IntStream.rangeClosed(1, 10)
            .mapToObj(i -> asset("주식" + i, String.valueOf(11 - i)))
            .toList();
    var levels =
        holdings.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    PortfolioAssetRequest::assetName, a -> ImpactLevel.HIGH));
    assertThat(PortfolioAnalysisSelection.select(holdings, levels))
        .extracting(PortfolioAssetRequest::assetName)
        .containsExactly("주식1", "주식2", "주식3", "주식4", "주식5");
    assertThat(PortfolioAnalysisSelection.select(holdings.reversed(), levels))
        .isEqualTo(PortfolioAnalysisSelection.select(holdings, levels));
  }

  @Test
  void refreshAlsoLimitsTargetsAndKeepsExcludedAssetsWhenSelectionChanges() {
    var holdings =
        List.of(
            asset("A", "30"),
            asset("B", "25"),
            asset("C", "20"),
            asset("D", "15"),
            asset("E", "9"),
            asset("F", "1"));
    when(assets.cachedImpactLevels(anyList())).thenReturn(Map.of());
    when(assets.analyze(anyList())).thenAnswer(i -> result(i.getArgument(0)));
    when(narrative.summarize(any())).thenReturn("선별한 종목만 분석했어요.");
    var previous = service.analyze(new PortfolioAnalysisRequest(holdings), true);
    when(assets.cachedImpactLevels(anyList())).thenReturn(Map.of("F", ImpactLevel.HIGH));
    when(ai.refresh(anyList(), anyString())).thenAnswer(i -> result(i.getArgument(0)));

    var response = service.refresh(new PortfolioAnalysisRequest(holdings), previous);

    assertThat(response.impacts())
        .extracting(PortfolioAssetImpactResponse::assetName)
        .containsExactlyInAnyOrder("A", "B", "C", "F", "D");
    assertThat(response.excludedAssets())
        .extracting(PortfolioAnalysisResponse.ExcludedAsset::assetName)
        .containsExactly("E");
    assertThat(response.totalAssetCount()).isEqualTo(6);
    assertThat(response.overallScore()).isNull();
    verify(ai).refresh(argThat(targets -> targets.size() == 5), anyString());
  }

  private static PortfolioAssetRequest asset(String name, String weight) {
    return new PortfolioAssetRequest(name, new BigDecimal(weight));
  }

  private static PortfolioAnalysisResult result(List<PortfolioAnalysisTarget> targets) {
    return new PortfolioAnalysisResult(
        "",
        targets.stream()
            .map(
                t ->
                    new PortfolioAnalysisResult.AssetImpactResult(
                        t.assetName(),
                        ImpactDirection.POSITIVE,
                        ImpactLevel.HIGH,
                        "실적이 늘었어요.",
                        "수요가 늘었어요.",
                        "실적을 확인해요.",
                        "이익이 늘었어요.",
                        "이익이 늘었어요.",
                        List.of(
                            new PortfolioAnalysisResult.Source(
                                "공식 발표", "https://example.com/" + t.assetName())),
                        targets.indexOf(t) + 1))
            .toList(),
        List.of());
  }
}
