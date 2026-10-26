package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.investmentcalendar.domain.*;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.junit.jupiter.api.Test;

class EmailDigestSupplementServiceTest {
  private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
  private final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
  private final PortfolioHoldingHistoryRepository histories =
      mock(PortfolioHoldingHistoryRepository.class);
  private final PortfolioDailySnapshotRepository snapshots =
      mock(PortfolioDailySnapshotRepository.class);
  private final MarketTradingDayRepository tradingDays = mock(MarketTradingDayRepository.class);
  private final InvestmentEventRepository events = mock(InvestmentEventRepository.class);
  private final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
  private final EmailDigestSupplementService service =
      new EmailDigestSupplementService(
          portfolios, holdings, histories, snapshots, tradingDays, events, deliveries);
  private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
  private final LocalDate base = LocalDate.of(2026, 10, 23);

  @Test
  void withoutHoldingsProvidesOnlyEconomyEventsAndLimitsToTwoUnsentEvents() {
    var first = event(1L, 86400, InvestmentEventType.US_CPI);
    var second = event(2L, 86401, InvestmentEventType.KOREA_BASE_RATE);
    var third = event(3L, 86402, InvestmentEventType.US_CPI);
    var corporate = event(4L, 86403, InvestmentEventType.CORPORATE_EARNINGS);
    when(events.findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
            any(), any()))
        .thenReturn(List.of(first, second, third, corporate));
    String firstKey = "1@" + first.getAnnouncedAt().getEpochSecond();
    when(deliveries.findCoveredEventKeys(eq(1L), anyList())).thenReturn(List.of(firstKey));
    var result = service.compose(1L, now);
    assertThat(result.assets()).isNull();
    assertThat(result.events())
        .extracting(item -> item.key())
        .containsExactly(
            "2@" + second.getAnnouncedAt().getEpochSecond(),
            "3@" + third.getAnnouncedAt().getEpochSecond());
    verifyNoInteractions(snapshots);
  }

  @Test
  void sameDayNeedsLateCollectionAndMustStillBeUpcoming() {
    var late = event(1L, 3600, InvestmentEventType.US_CPI);
    when(late.getUpdatedAt()).thenReturn(now.minusSeconds(60));
    var early = event(2L, 7200, InvestmentEventType.US_CPI);
    when(early.getUpdatedAt()).thenReturn(now.minusSeconds(90000));
    var past = event(3L, -1, InvestmentEventType.US_CPI);
    when(events.findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
            any(), any()))
        .thenReturn(List.of(past, late, early));
    assertThat(service.compose(1L, now).events())
        .extracting(item -> item.title())
        .containsExactly("일정1");
  }

  @Test
  void readsOnlyTheLatestCollectedSnapshotAndSuppressesAfterHoldingChangesOrPriorNotice() {
    var portfolio = mock(Portfolio.class);
    when(portfolio.getId()).thenReturn(10L);
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.of(portfolio));
    var holding = mock(PortfolioHolding.class);
    var asset = mock(Asset.class);
    when(holding.getAsset()).thenReturn(asset);
    when(holdings.findAllByPortfolioIdOrderById(10L)).thenReturn(List.of(holding));
    var day = mock(MarketTradingDay.class);
    when(day.getBaseDate()).thenReturn(base);
    when(tradingDays
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                any()))
        .thenReturn(Optional.of(day));
    var snapshot = mock(PortfolioDailySnapshot.class);
    when(snapshot.getTotalEvaluationAmount()).thenReturn(new BigDecimal("102"));
    when(snapshot.getDailyProfitLoss()).thenReturn(new BigDecimal("2"));
    when(snapshot.getDailyReturnRate()).thenReturn(new BigDecimal("2"));
    when(snapshots.findByPortfolioIdAndBaseDate(10L, base)).thenReturn(Optional.of(snapshot));
    assertThat(service.compose(1L, now).assets().baseDate()).isEqualTo(base);
    when(deliveries.hasCoveredAssetSummary(1L, base.toString())).thenReturn(true);
    assertThat(service.compose(1L, now).assets()).isNull();
    when(deliveries.hasCoveredAssetSummary(1L, base.toString())).thenReturn(false);
    when(histories
            .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
                10L, base.plusDays(1), LocalDate.of(2026, 10, 26)))
        .thenReturn(List.of(mock(PortfolioHoldingHistory.class)));
    assertThat(service.compose(1L, now).assets()).isNull();
  }

  private InvestmentEvent event(long id, long seconds, InvestmentEventType type) {
    var event = mock(InvestmentEvent.class);
    when(event.getId()).thenReturn(id);
    when(event.getType()).thenReturn(type);
    when(event.getTitle()).thenReturn("일정" + id);
    when(event.getAnnouncedAt()).thenReturn(now.plusSeconds(seconds));
    when(event.getSourceUrl()).thenReturn("https://example.invalid/source");
    return event;
  }
}
