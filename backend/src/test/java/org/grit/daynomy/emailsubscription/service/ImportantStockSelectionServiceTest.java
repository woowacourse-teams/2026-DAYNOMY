package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ImportantStockSelectionServiceTest {

  @Mock private EmailSubscriptionRepository subscriptions;
  @Mock private PortfolioRepository portfolios;
  @Mock private PortfolioHoldingRepository holdings;
  @Mock private MarketTradingDayRepository tradingDays;
  @Mock private StockDailyPriceRepository prices;
  @InjectMocks private ImportantStockSelectionService service;

  private final LocalDate runDate = LocalDate.of(2026, 10, 19);

  @Test
  void doesNotReadPortfolioOrPricesForANonSubscriber() {
    when(subscriptions.findById(1L)).thenReturn(Optional.of(new EmailSubscription(member())));

    assertThat(service.select(1L, runDate)).isEmpty();
    verifyNoInteractions(portfolios, holdings, tradingDays, prices);
  }

  @Test
  void doesNotSelectForAWithdrawnSubscriber() {
    Member member = member();
    EmailSubscription subscription = subscribed(member);
    member.withdraw();
    when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));

    assertThat(service.select(1L, runDate)).isEmpty();
    verifyNoInteractions(portfolios, holdings, tradingDays, prices);
  }

  @Test
  void doesNotReadPricesWhenNoPortfolioExists() {
    when(subscriptions.findById(1L)).thenReturn(Optional.of(subscribed(member())));
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.empty());

    assertThat(service.select(1L, runDate)).isEmpty();
    verifyNoInteractions(holdings, tradingDays, prices);
  }

  @Test
  void readsTwoExactTradingDatesInBatchesInsteadOfUsingArbitrarilyOldPrices() {
    savedHolding();
    LocalDate current = LocalDate.of(2026, 10, 16);
    LocalDate previous = LocalDate.of(2026, 10, 15);
    tradingDates(current, previous);
    Asset asset = asset();
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L), current))
        .thenReturn(List.of(new StockDailyPrice(asset, current, new BigDecimal("105"))));
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L), previous))
        .thenReturn(List.of(new StockDailyPrice(asset, previous, new BigDecimal("100"))));

    var selection = service.select(1L, runDate).orElseThrow();

    assertThat(selection.baseDate()).isEqualTo(current);
    assertThat(selection.previousBaseDate()).isEqualTo(previous);
    assertThat(selection.stocks()).hasSize(1);
    assertThat(selection.stocks().getFirst().assetId()).isEqualTo(10L);
    verify(prices).findAllByAssetIdInAndBaseDate(List.of(10L), current);
    verify(prices).findAllByAssetIdInAndBaseDate(List.of(10L), previous);
  }

  @Test
  void keepsMissingPricesDistinctFromNoImportantChanges() {
    savedHolding();
    LocalDate current = LocalDate.of(2026, 10, 16);
    LocalDate previous = LocalDate.of(2026, 10, 15);
    tradingDates(current, previous);
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L), current)).thenReturn(List.of());
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L), previous)).thenReturn(List.of());

    var selection = service.select(1L, runDate).orElseThrow();

    assertThat(selection.completePrices()).isFalse();
    assertThat(selection.stocks()).isEmpty();
  }

  @Test
  void doesNotSelectWithoutACollectedTradingDate() {
    savedHolding();
    when(tradingDays
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                runDate))
        .thenReturn(Optional.empty());

    assertThat(service.select(1L, runDate)).isEmpty();
    verifyNoInteractions(prices);
  }

  @Test
  void doesNotSubstituteAnOlderPriceWhenThePreviousTradingDateIsUnknown() {
    savedHolding();
    LocalDate current = LocalDate.of(2026, 10, 16);
    when(tradingDays
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                runDate))
        .thenReturn(Optional.of(new MarketTradingDay(current, true)));
    when(tradingDays.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(current))
        .thenReturn(Optional.empty());

    assertThat(service.select(1L, runDate)).isEmpty();
    verifyNoInteractions(prices);
  }

  private void savedHolding() {
    when(subscriptions.findById(1L)).thenReturn(Optional.of(subscribed(member())));
    Portfolio portfolio = mock(Portfolio.class);
    when(portfolio.getId()).thenReturn(100L);
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.of(portfolio));
    when(holdings.findAllByPortfolioIdOrderById(100L))
        .thenReturn(List.of(new PortfolioHolding(portfolio, asset(), 2, BigDecimal.TEN)));
  }

  private void tradingDates(LocalDate current, LocalDate previous) {
    when(tradingDays
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                runDate))
        .thenReturn(Optional.of(new MarketTradingDay(current, true)));
    when(tradingDays.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(current))
        .thenReturn(Optional.of(new MarketTradingDay(previous, true)));
  }

  private Asset asset() {
    Asset asset = new Asset("종목", AssetCategory.STOCK, "005930");
    ReflectionTestUtils.setField(asset, "id", 10L);
    return asset;
  }

  private Member member() {
    return Member.createGoogleMember("google-id", "google@example.invalid", "회원", null);
  }

  private EmailSubscription subscribed(Member member) {
    EmailSubscription subscription = new EmailSubscription(member);
    subscription.subscribe(Instant.parse("2026-10-18T00:00:00Z"));
    return subscription;
  }
}
