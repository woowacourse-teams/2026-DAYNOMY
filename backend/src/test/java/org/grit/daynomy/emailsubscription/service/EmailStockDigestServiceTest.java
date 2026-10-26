package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection;
import org.grit.daynomy.emailsubscription.repository.CommonStockAnalysisRepository;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.junit.jupiter.api.Test;

class EmailStockDigestServiceTest {
  private final ImportantStockSelectionService selection =
      mock(ImportantStockSelectionService.class);
  private final CommonStockAnalysisRepository analyses = mock(CommonStockAnalysisRepository.class);
  private final EmailStockDigestService service = new EmailStockDigestService(selection, analyses);
  private final Instant asOf = Instant.parse("2026-10-26T00:00:00Z");
  private final LocalDate date = LocalDate.of(2026, 10, 26);

  @Test
  void skipsIneligibleMember() {
    when(selection.select(1L, date)).thenReturn(Optional.empty());
    assertThat(service.compose(1L, asOf, List.of(10L))).isEmpty();
    verifyNoInteractions(analyses);
  }

  @Test
  void personalizesSharedAnalysisAndOmitsUnknownNumbers() {
    var analysis = analysis();
    when(analyses.findAllById(List.of(10L))).thenReturn(List.of(analysis));
    selected("40", "2");
    var first = service.compose(1L, asOf, List.of(10L)).orElseThrow();
    selected("5", "0.25");
    var second = service.compose(1L, asOf, List.of(10L)).orElseThrow();
    assertThat(first.body()).contains("비중: 40%", "기여도: 2%p", "부분 분석", "2026-10-23");
    assertThat(second.body()).contains("비중: 5%", "기여도: 0.25%p");
    assertThat(first.stocks().getFirst().content()).isSameAs(second.stocks().getFirst().content());
    selected(null, null);
    assertThat(service.compose(1L, asOf, List.of(10L)).orElseThrow().body())
        .contains("종목 등락률: 5%")
        .doesNotContain("비중:", "기여도:");
  }

  @Test
  void excludesUnverifiedStaleChangedIdentityAndUnheldStocks() {
    selected("40", "2");
    var analysis = analysis();
    when(analyses.findAllById(List.of(10L))).thenReturn(List.of(analysis));
    when(analysis.getStatus()).thenReturn(CommonStockAnalysis.Status.PENDING);
    assertThat(service.compose(1L, asOf, List.of(10L))).isEmpty();
    when(analysis.getStatus()).thenReturn(CommonStockAnalysis.Status.VERIFIED);
    when(analysis.getAsOf()).thenReturn(asOf.minusSeconds(1));
    assertThat(service.compose(1L, asOf, List.of(10L))).isEmpty();
    when(analysis.getAsOf()).thenReturn(asOf);
    when(analysis.getAssetCode()).thenReturn("005935");
    assertThat(service.compose(1L, asOf, List.of(10L))).isEmpty();
    when(analysis.getAssetCode()).thenReturn("005930");
    when(analysis.getAsset().getId()).thenReturn(99L);
    assertThat(service.compose(1L, asOf, List.of(10L))).isEmpty();
  }

  private void selected(String weight, String contribution) {
    when(selection.select(1L, date))
        .thenReturn(
            Optional.of(
                new ImportantStockSelection(
                    LocalDate.of(2026, 10, 23),
                    LocalDate.of(2026, 10, 22),
                    weight != null,
                    List.of(
                        new ImportantStockSelection.Stock(
                            1L,
                            new BigDecimal("5"),
                            weight == null ? null : new BigDecimal(weight),
                            contribution == null ? null : new BigDecimal(contribution))))));
  }

  private CommonStockAnalysis analysis() {
    var analysis = mock(CommonStockAnalysis.class);
    var asset = mock(Asset.class);
    when(analysis.getAsset()).thenReturn(asset);
    when(asset.getId()).thenReturn(1L);
    when(asset.getName()).thenReturn("삼성전자");
    when(asset.getAssetCode()).thenReturn("005930");
    when(asset.getMarket()).thenReturn(StockMarket.KOSPI);
    when(asset.getCategory()).thenReturn(AssetCategory.STOCK);
    when(analysis.getId()).thenReturn(10L);
    when(analysis.getStatus()).thenReturn(CommonStockAnalysis.Status.VERIFIED);
    when(analysis.getAsOf()).thenReturn(asOf);
    when(analysis.getAssetCode()).thenReturn("005930");
    when(analysis.getMarket()).thenReturn(StockMarket.KOSPI);
    when(analysis.getCategory()).thenReturn(AssetCategory.STOCK);
    when(analysis.getContent())
        .thenReturn(
            new CommonStockAnalysisContent(
                ImpactDirection.POSITIVE,
                ImpactLevel.HIGH,
                "매출 증가",
                "",
                "",
                "공식 실적 발표 기준입니다.",
                List.of(new Source("실적 발표", "https://example.com/results"))));
    return analysis;
  }
}
