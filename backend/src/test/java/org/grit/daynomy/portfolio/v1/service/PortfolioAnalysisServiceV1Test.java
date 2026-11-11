package org.grit.daynomy.portfolio.v1.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.portfolio.ai.*;
import org.grit.daynomy.portfolio.dto.*;
import org.grit.daynomy.portfolio.service.*;
import org.grit.daynomy.portfolio.v1.ai.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioAnalysisServiceV1Test {
  private final PortfolioAnalysisAiClient sharedAi = mock(PortfolioAnalysisAiClient.class);
  private final PortfolioAssetAnalysisService assets = mock(PortfolioAssetAnalysisService.class);
  private final PortfolioAnalysisNarrative narrative = mock(PortfolioAnalysisNarrative.class);
  private final OpenAiPortfolioAnalysisClientV1 client =
      mock(OpenAiPortfolioAnalysisClientV1.class);
  private final PortfolioAnalysisServiceV1 service =
      new PortfolioAnalysisServiceV1(
          sharedAi,
          assets,
          narrative,
          new GuestPortfolioAnalysisTrial(),
          client,
          mock(org.grit.daynomy.portfolio.service.PortfolioAnalysisPriceService.class));
  private final PortfolioAnalysisRequest request =
      new PortfolioAnalysisRequest(
          List.of(
              new PortfolioAssetRequest("삼성전자", new BigDecimal("60")),
              new PortfolioAssetRequest("NAVER", new BigDecimal("40"))));

  @Test
  void passesAllOriginalWeightsToV1AndSummarizesTheConvertedResponse() {
    when(client.analyze(anyList()))
        .thenReturn(
            new PortfolioAnalysisResult(
                "", List.of(impact("NAVER", 1), impact("삼성전자", 2)), List.of()));
    when(narrative.summarize(any())).thenReturn("요약이에요.");
    var result = service.analyze(request, true);
    verify(client)
        .analyze(
            List.of(
                new PortfolioAnalysisTargetV1("삼성전자", new BigDecimal("60")),
                new PortfolioAnalysisTargetV1("NAVER", new BigDecimal("40"))));
    var prepared = ArgumentCaptor.forClass(PortfolioAnalysisResponse.class);
    verify(narrative).summarize(prepared.capture());
    assertThat(result).isEqualTo(prepared.getValue().withOverallImpact("요약이에요."));
    assertThat(result.impacts())
        .extracting(PortfolioAssetImpactResponse::weight)
        .containsExactly(new BigDecimal("40"), new BigDecimal("60"));
    assertThat(result.impacts()).allSatisfy(impact -> assertThat(impact.sources()).hasSize(1));
    verifyNoInteractions(sharedAi, assets);
  }

  @Test
  void aiFailureDoesNotStartNarrativeOrFallBackToSharedAnalysis() {
    var failure = new IllegalStateException("provider unavailable");
    when(client.analyze(anyList())).thenThrow(failure);
    assertThatThrownBy(() -> service.analyze(request, true)).isSameAs(failure);
    verifyNoInteractions(narrative, sharedAi, assets);
  }

  @Test
  void narrativeFailureIsNotReturnedAsSuccessfulAnalysis() {
    when(client.analyze(anyList()))
        .thenReturn(
            new PortfolioAnalysisResult(
                "", List.of(impact("NAVER", 1), impact("삼성전자", 2)), List.of()));
    var failure = new IllegalStateException("summary unavailable");
    when(narrative.summarize(any())).thenThrow(failure);
    assertThatThrownBy(() -> service.analyze(request, true)).isSameAs(failure);
    verify(client, times(1)).analyze(anyList());
  }

  private PortfolioAnalysisResult.AssetImpactResult impact(String name, int order) {
    return new PortfolioAnalysisResult.AssetImpactResult(
        name,
        org.grit.daynomy.market.domain.asset.ImpactDirection.POSITIVE,
        org.grit.daynomy.market.domain.asset.ImpactLevel.HIGH,
        "새 실적",
        "매출 증가",
        "수익성 개선",
        "공시 확인",
        "매출이 증가했다.",
        List.of(new PortfolioAnalysisResult.Source("실적", "https://example.invalid/earnings")),
        order);
  }
}
