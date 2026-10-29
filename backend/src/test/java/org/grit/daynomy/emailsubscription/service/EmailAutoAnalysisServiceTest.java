package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.repository.*;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.junit.jupiter.api.*;

class EmailAutoAnalysisServiceTest {
  final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  final ImportantStockSelectionService selection = mock(ImportantStockSelectionService.class);
  final PortfolioRepository portfolios = mock(PortfolioRepository.class);
  final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
  final SavedPortfolioAnalysisService analyses = mock(SavedPortfolioAnalysisService.class);
  final EmailAutoAnalysisRepository runs = mock(EmailAutoAnalysisRepository.class);
  final Instant start = NOW.minusSeconds(7200);
  final UUID id = UUID.randomUUID();
  final SavedPortfolioAnalysisResponse previous = mock(SavedPortfolioAnalysisResponse.class);

  EmailAutoAnalysisService service(Instant now, int daily, int monthly) {
    return new EmailAutoAnalysisService(
        subscriptions,
        selection,
        portfolios,
        holdings,
        analyses,
        runs,
        daily,
        monthly,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  @BeforeEach
  void setup() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L));
    when(subscriptions.isEligible(1L)).thenReturn(true);
    var portfolio = mock(Portfolio.class);
    when(portfolio.getId()).thenReturn(10L);
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.of(portfolio));
    when(analyses.get(1L)).thenReturn(previous);
    when(previous.analysisId()).thenReturn(100L);
    var impact = mock(PortfolioAssetImpactResponse.class);
    when(impact.assetName()).thenReturn("삼성전자");
    var analysis = mock(PortfolioAnalysisResponse.class);
    when(analysis.impacts()).thenReturn(List.of(impact));
    when(previous.analysis()).thenReturn(analysis);
    var asset = mock(Asset.class);
    when(asset.getId()).thenReturn(1L);
    when(asset.getName()).thenReturn("삼성전자");
    var holding = mock(PortfolioHolding.class);
    when(holding.getAsset()).thenReturn(asset);
    when(holdings.findAllByPortfolioIdOrderById(10L)).thenReturn(List.of(holding));
  }

  void priceSignal() {
    when(selection.select(1L, MAIL_DATE))
        .thenReturn(
            Optional.of(
                new ImportantStockSelection(
                    MAIL_DATE.minusDays(3),
                    MAIL_DATE.minusDays(4),
                    true,
                    List.of(
                        new ImportantStockSelection.Stock(
                            1L, java.math.BigDecimal.TEN, null, null)))));
  }

  @Test
  void noImportantChangesNeverCallAI() {
    service(start, 10, 100).runDaily();
    verify(analyses, never()).refreshAutomatically(anyLong(), anySet());
    verify(runs, never()).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
  }

  @Test
  void runsExistingAnalysisOnceAfterReservingPersistentLimits() {
    priceSignal();
    when(runs.claim(
            eq(1L), eq(MAIL_DATE), eq(List.of("price:1:2026-10-23")), eq(10), eq(100), eq(start)))
        .thenReturn(
            Optional.of(new EmailAutoAnalysisRepository.Claim(id, List.of("price:1:2026-10-23"))));
    var completed = mock(SavedPortfolioAnalysisResponse.class);
    when(completed.analysisId()).thenReturn(101L);
    when(completed.summaryStatus()).thenReturn("READY");
    when(analyses.refreshAutomatically(1L, Set.of("삼성전자"))).thenReturn(completed);
    service(start, 10, 100).runDaily();
    var order = inOrder(runs, analyses);
    order.verify(runs).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
    order.verify(analyses).refreshAutomatically(1L, Set.of("삼성전자"));
    order.verify(runs).complete(id, 101L, true, start);
  }

  @Test
  void verifiedEarningsCanTriggerWithoutPriceSignals() {
    when(runs.earningsSignals(anyList(), any(), any()))
        .thenReturn(Map.of(1L, List.of("earnings:1:key:revision")));
    service(start, 10, 100).runDaily();
    verify(runs)
        .claim(
            eq(1L),
            eq(MAIL_DATE),
            eq(List.of("earnings:1:key:revision")),
            eq(10),
            eq(100),
            eq(start));
  }

  @Test
  void analyzesOnlyStocksWithFreshReservedSignals() {
    priceSignal();
    var second = mock(Asset.class);
    when(second.getId()).thenReturn(2L);
    when(second.getName()).thenReturn("NAVER");
    var holding = mock(PortfolioHolding.class);
    when(holding.getAsset()).thenReturn(second);
    var positions = new ArrayList<>(holdings.findAllByPortfolioIdOrderById(10L));
    positions.add(holding);
    when(holdings.findAllByPortfolioIdOrderById(10L)).thenReturn(positions);
    var impact = mock(PortfolioAssetImpactResponse.class);
    when(impact.assetName()).thenReturn("NAVER");
    var impacts = new ArrayList<>(previous.analysis().impacts());
    impacts.add(impact);
    when(previous.analysis().impacts()).thenReturn(impacts);
    when(runs.earningsSignals(anyList(), any(), any()))
        .thenReturn(Map.of(2L, List.of("earnings:2:key:revision")));
    when(runs.claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any()))
        .thenReturn(
            Optional.of(
                new EmailAutoAnalysisRepository.Claim(id, List.of("earnings:2:key:revision"))));
    var completed = mock(SavedPortfolioAnalysisResponse.class);
    when(analyses.refreshAutomatically(1L, Set.of("NAVER"))).thenReturn(completed);
    service(start, 10, 100).runDaily();
    verify(analyses).refreshAutomatically(1L, Set.of("NAVER"));
    verify(analyses, never()).refreshAutomatically(1L, Set.of("삼성전자", "NAVER"));
  }

  @Test
  void doesNotExpandToStockOutsideExistingAnalysis() {
    when(runs.earningsSignals(anyList(), any(), any()))
        .thenReturn(Map.of(2L, List.of("earnings:2:key:revision")));
    service(start, 10, 100).runDaily();
    verify(runs, never()).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
    verify(analyses, never()).refreshAutomatically(anyLong(), anySet());
  }

  @Test
  void duplicateOrExhaustedReservationDoesNotCallAI() {
    priceSignal();
    service(start, 10, 100).runDaily();
    verify(analyses, never()).refreshAutomatically(anyLong(), anySet());
  }

  @Test
  void manualAnalysisAlreadyCheckedThisMorningIsNotRepeated() {
    priceSignal();
    when(previous.lastSucceededAt()).thenReturn(start);
    service(start, 10, 100).runDaily();
    verify(runs, never()).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
  }

  @Test
  void rechecksUnsubscribeAfterReservation() {
    priceSignal();
    when(subscriptions.isEligible(1L)).thenReturn(true, false);
    when(runs.claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any()))
        .thenReturn(
            Optional.of(new EmailAutoAnalysisRepository.Claim(id, List.of("price:1:2026-10-23"))));
    service(start, 10, 100).runDaily();
    verify(analyses, never()).refreshAutomatically(anyLong(), anySet());
    verify(runs).complete(id, null, false, start);
  }

  @Test
  void failedAIConsumesReservationAndDoesNotSendAnyMail() {
    priceSignal();
    when(runs.claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any()))
        .thenReturn(
            Optional.of(new EmailAutoAnalysisRepository.Claim(id, List.of("price:1:2026-10-23"))));
    when(analyses.refreshAutomatically(1L, Set.of("삼성전자")))
        .thenThrow(new IllegalStateException("test"));
    assertThatCode(() -> service(start, 10, 100).runDaily()).doesNotThrowAnyException();
    verify(runs).complete(id, null, false, start);
    verify(analyses, times(1)).refreshAutomatically(1L, Set.of("삼성전자"));
  }

  @Test
  void noExecutionBeforeSevenAfterCutoffOrWithoutLimits() {
    service(start.minusSeconds(1), 10, 100).runDaily();
    service(NOW.minusSeconds(900), 10, 100).runDaily();
    service(start, 0, 100).runDaily();
    service(start, 10, 0).runDaily();
    verify(subscriptions, never()).findEligibleMemberIds(anyLong(), any());
  }
}
