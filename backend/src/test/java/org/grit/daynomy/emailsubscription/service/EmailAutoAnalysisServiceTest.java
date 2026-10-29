package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.portfolio.domain.Portfolio;
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
    verify(analyses, never()).refreshAutomatically(anyLong());
    verify(runs, never()).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
  }

  @Test
  void runsExistingAnalysisOnceAfterReservingPersistentLimits() {
    priceSignal();
    when(runs.claim(
            eq(1L), eq(MAIL_DATE), eq(List.of("price:2026-10-23")), eq(10), eq(100), eq(start)))
        .thenReturn(Optional.of(id));
    var completed = mock(SavedPortfolioAnalysisResponse.class);
    when(completed.analysisId()).thenReturn(101L);
    when(completed.summaryStatus()).thenReturn("READY");
    when(analyses.refreshAutomatically(1L)).thenReturn(completed);
    service(start, 10, 100).runDaily();
    var order = inOrder(runs, analyses);
    order.verify(runs).claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any());
    order.verify(analyses).refreshAutomatically(1L);
    order.verify(runs).complete(id, 101L, true, start);
  }

  @Test
  void verifiedEarningsCanTriggerWithoutPriceSignals() {
    when(runs.earningsSignals(anyList(), any(), any()))
        .thenReturn(List.of("earnings:1:key:revision"));
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
  void duplicateOrExhaustedReservationDoesNotCallAI() {
    priceSignal();
    service(start, 10, 100).runDaily();
    verify(analyses, never()).refreshAutomatically(anyLong());
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
        .thenReturn(Optional.of(id));
    service(start, 10, 100).runDaily();
    verify(analyses, never()).refreshAutomatically(anyLong());
    verify(runs).complete(id, null, false, start);
  }

  @Test
  void failedAIConsumesReservationAndDoesNotSendAnyMail() {
    priceSignal();
    when(runs.claim(anyLong(), any(), anyList(), anyInt(), anyInt(), any()))
        .thenReturn(Optional.of(id));
    when(analyses.refreshAutomatically(1L)).thenThrow(new IllegalStateException("test"));
    assertThatCode(() -> service(start, 10, 100).runDaily()).doesNotThrowAnyException();
    verify(runs).complete(id, null, false, start);
    verify(analyses, times(1)).refreshAutomatically(1L);
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
