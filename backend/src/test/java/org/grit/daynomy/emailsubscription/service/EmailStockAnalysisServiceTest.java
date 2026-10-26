package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisDocumentClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailStockAnalysisServiceTest {
  @Mock private ImportantStockSelectionService selection;
  @Mock private AssetRepository assets;
  @Mock private CommonStockAnalysisService common;
  @Mock private PortfolioAnalysisAiClient ai;
  @Mock private PortfolioAnalysisDocumentClient documents;
  private EmailStockAnalysisService service;
  private static final Instant AS_OF = Instant.parse("2026-10-26T00:00:00Z");
  private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
  private static final String URL = "https://example.com/results/1";
  private static final String QUOTE = "삼성전자 매출이 10% 증가했습니다.";

  @BeforeEach
  void setUp() {
    service = new EmailStockAnalysisService(selection, assets, common, ai, documents, "model");
  }

  @Test
  void doesNotCallExternalServicesWhenNothingWasSelected() {
    when(selection.select(1L, LocalDate.of(2026, 10, 26))).thenReturn(Optional.empty());
    assertThat(service.analyze(1L, AS_OF)).isEmpty();
    verifyNoInteractions(assets, common, ai, documents);
  }

  @Test
  void reusesVerifiedAnalysisWithoutCallingAiOrFetchingSources() {
    selected();
    var saved = mock(CommonStockAnalysis.class);
    when(common.findReusable(any())).thenReturn(Optional.of(saved));
    assertThat(service.analyze(1L, AS_OF)).containsExactly(saved);
    verifyNoInteractions(ai, documents);
  }

  @Test
  void connectsSelectionGenerationOriginalVerificationAndStorage() {
    selected();
    var saved = pending();
    when(common.findReusable(any()))
        .thenAnswer(
            invocation ->
                saved.getStatus() == CommonStockAnalysis.Status.VERIFIED
                    ? Optional.of(saved)
                    : Optional.empty());
    when(common.findStored(any())).thenReturn(Optional.empty(), Optional.of(saved));
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation -> {
              List<PortfolioAnalysisTarget> targets = invocation.getArgument(0);
              assertThat(targets.getFirst().assetName()).isEqualTo("삼성전자 [005930, KOSPI, STOCK]");
              return new PortfolioAnalysisResult(
                  "회원별 설명은 사용하지 않음",
                  List.of(
                      new PortfolioAnalysisResult.AssetImpactResult(
                          targets.getFirst().assetName(),
                          ImpactDirection.POSITIVE,
                          ImpactLevel.HIGH,
                          "매출이 10% 증가했어요.",
                          "확인해요.",
                          "확인해요.",
                          "발표를 참고했어요.",
                          QUOTE,
                          saved.getContent().sources(),
                          1)),
                  saved.getContent().sources());
            });
    when(documents.fetchAll(List.of(URL)))
        .thenReturn(
            Map.of(URL, Optional.of(new PortfolioAnalysisSourceDocument("삼성전자 실적", QUOTE, DATE))));
    doAnswer(
            invocation -> {
              saved.recordVerification(invocation.getArgument(1));
              return null;
            })
        .when(common)
        .recordVerification(eq(100L), any(CommonStockAnalysisVerification.class));
    assertThat(service.analyze(1L, AS_OF)).containsExactly(saved);
    verify(common).storePending(any(), any());
    verify(ai, never()).refresh(anyList(), any());
  }

  @Test
  void unavailableOriginalsKeepPendingAnalysisWithoutCallingAiAgain() {
    selected();
    when(common.findReusable(any())).thenReturn(Optional.empty());
    when(common.findStored(any())).thenReturn(Optional.of(pending()));
    when(documents.fetchAll(List.of(URL))).thenReturn(Map.of(URL, Optional.empty()));
    assertThat(service.analyze(1L, AS_OF)).isEmpty();
    verifyNoInteractions(ai);
    verify(common, never()).recordVerification(any(), any());
  }

  @Test
  void rejectedAnalysisDoesNotTriggerAnotherAiRequest() {
    selected();
    var rejected = mock(CommonStockAnalysis.class);
    when(rejected.getStatus()).thenReturn(CommonStockAnalysis.Status.REJECTED);
    when(common.findReusable(any())).thenReturn(Optional.empty());
    when(common.findStored(any())).thenReturn(Optional.of(rejected));
    assertThat(service.analyze(1L, AS_OF)).isEmpty();
    verifyNoInteractions(ai, documents);
  }

  @Test
  void mismatchedStockResponsesAreNotStored() {
    selected();
    when(common.findReusable(any())).thenReturn(Optional.empty());
    when(common.findStored(any())).thenReturn(Optional.empty());
    when(ai.analyze(anyList())).thenReturn(new PortfolioAnalysisResult("", List.of(), List.of()));
    assertThat(service.analyze(1L, AS_OF)).isEmpty();
    verify(common, never()).storePending(any(), any());
    verifyNoInteractions(documents);
  }

  @Test
  void aiFailuresDoNotBecomeUnchangedOrReusableResults() {
    selected();
    when(common.findReusable(any())).thenReturn(Optional.empty());
    when(common.findStored(any())).thenReturn(Optional.empty());
    when(ai.analyze(anyList()))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));
    assertThat(service.analyze(1L, AS_OF)).isEmpty();
    verify(common, never()).storePending(any(), any());
    verifyNoInteractions(documents);
  }

  @Test
  void executionAlsoEnforcesTheThreeStockLimit() {
    var stocks =
        List.of(10L, 11L, 12L, 13L).stream()
            .map(
                id ->
                    new ImportantStockSelection.Stock(
                        id, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE))
            .toList();
    when(selection.select(1L, LocalDate.of(2026, 10, 26)))
        .thenReturn(
            Optional.of(new ImportantStockSelection(DATE, DATE.minusDays(1), true, stocks)));
    var first = asset();
    var second = asset();
    var third = asset();
    ReflectionTestUtils.setField(second, "id", 11L);
    ReflectionTestUtils.setField(third, "id", 12L);
    when(assets.findAllById(List.of(10L, 11L, 12L))).thenReturn(List.of(first, second, third));
    var reused = mock(CommonStockAnalysis.class);
    when(common.findReusable(any())).thenReturn(Optional.of(reused));
    assertThat(service.analyze(1L, AS_OF)).hasSize(3);
    verifyNoInteractions(ai, documents);
  }

  private void selected() {
    when(selection.select(1L, LocalDate.of(2026, 10, 26)))
        .thenReturn(
            Optional.of(
                new ImportantStockSelection(
                    DATE,
                    DATE.minusDays(1),
                    true,
                    List.of(
                        new ImportantStockSelection.Stock(
                            10L, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)))));
    when(assets.findAllById(List.of(10L))).thenReturn(List.of(asset()));
  }

  private Asset asset() {
    Asset asset = Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, DATE);
    ReflectionTestUtils.setField(asset, "id", 10L);
    return asset;
  }

  private CommonStockAnalysis pending() {
    var content =
        new CommonStockAnalysisContent(
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            "매출이 10% 증가했어요.",
            "확인해요.",
            "확인해요.",
            "발표를 참고했어요.",
            List.of(
                new PortfolioAnalysisResult.Source(
                    "실적", URL, DATE, null, "매출이 10% 증가했어요.", QUOTE)));
    var analysis =
        new CommonStockAnalysis(
            asset(),
            new CommonStockAnalysisKey(10L, "model", "v1", "e1", AS_OF),
            content,
            "a".repeat(64),
            AS_OF.plusSeconds(1));
    ReflectionTestUtils.setField(analysis, "id", 100L);
    return analysis;
  }
}
