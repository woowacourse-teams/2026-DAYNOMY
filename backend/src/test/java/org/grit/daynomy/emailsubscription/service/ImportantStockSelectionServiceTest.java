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
  @Mock private org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository events;
  @Mock private org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository deliveries;
  @Mock private org.grit.daynomy.emailsubscription.repository.EmailStockContentRepository contents;
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

  @Test
  void excludesUnconvertedMixedCurrencyWeightsFromPersonalSelection() {
    savedHolding();
    LocalDate current = LocalDate.of(2026, 10, 16);
    LocalDate previous = current.minusDays(1);
    tradingDates(current, previous);
    Asset won = asset();
    Asset dollar =
        Asset.listedStock(
            "미국 종목", "US", org.grit.daynomy.asset.domain.StockMarket.NASDAQ, null, current);
    ReflectionTestUtils.setField(dollar, "id", 20L);
    Portfolio portfolio = mock(Portfolio.class);
    when(holdings.findAllByPortfolioIdOrderById(100L))
        .thenReturn(
            List.of(
                new PortfolioHolding(portfolio, won, 1, BigDecimal.TEN),
                new PortfolioHolding(portfolio, dollar, 1, BigDecimal.TEN)));
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L, 20L), current))
        .thenReturn(
            List.of(
                new StockDailyPrice(won, current, new BigDecimal("106")),
                new StockDailyPrice(dollar, current, new BigDecimal("1010"))));
    when(prices.findAllByAssetIdInAndBaseDate(List.of(10L, 20L), previous))
        .thenReturn(
            List.of(
                new StockDailyPrice(won, previous, new BigDecimal("100")),
                new StockDailyPrice(dollar, previous, new BigDecimal("1000"))));
    var selected = service.select(1L, runDate).orElseThrow();
    assertThat(selected.completePrices()).isFalse();
    assertThat(selected.stocks()).hasSize(1);
    assertThat(selected.stocks().getFirst().weight()).isNull();
    assertThat(selected.stocks().getFirst().contribution()).isNull();
  }

  @Test
  void selectsOfficialNextDayEarningsButRejectsUnrelatedAndUntrustedEvents() {
    savedHolding();
    LocalDate current = runDate.minusDays(3);
    tradingDates(current, current.minusDays(1));
    Asset korean =
        Asset.listedStock(
            "삼성전자", "005930", org.grit.daynomy.asset.domain.StockMarket.KOSPI, null, current);
    ReflectionTestUtils.setField(korean, "id", 10L);
    when(holdings.findAllByPortfolioIdOrderById(100L))
        .thenReturn(
            List.of(new PortfolioHolding(mock(Portfolio.class), korean, 1, BigDecimal.TEN)));
    var tomorrow =
        runDate
            .plusDays(1)
            .atTime(9, 0)
            .atZone(org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy.ZONE)
            .toInstant();
    var official =
        new org.grit.daynomy.investmentcalendar.domain.InvestmentEvent(
            org.grit.daynomy.investmentcalendar.domain.InvestmentEventType.CORPORATE_EARNINGS,
            "실적 발표",
            tomorrow,
            null,
            null,
            "원",
            "DART",
            "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261019000001",
            "earnings",
            "005930");
    when(events.findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(official));
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).hasSize(1);
    official.synchronize(
        "실적 발표",
        tomorrow,
        null,
        null,
        "원",
        "DART",
        "https://dart.fss.or.kr.evil.invalid/dsaf001/main.do?rcpNo=20261019000001",
        "005930");
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).isEmpty();
    official.synchronize(
        "실적 발표",
        tomorrow,
        null,
        null,
        "원",
        "DART",
        "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261019000001",
        "005935");
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).isEmpty();
  }

  @Test
  void includesRecentEarningsButNotAnUnpublishedResultLaterToday() {
    savedHolding();
    LocalDate current = runDate.minusDays(3);
    tradingDates(current, current.minusDays(1));
    Asset korean =
        Asset.listedStock(
            "삼성전자", "005930", org.grit.daynomy.asset.domain.StockMarket.KOSPI, null, current);
    ReflectionTestUtils.setField(korean, "id", 10L);
    when(holdings.findAllByPortfolioIdOrderById(100L))
        .thenReturn(
            List.of(new PortfolioHolding(mock(Portfolio.class), korean, 1, BigDecimal.TEN)));
    var yesterday =
        runDate
            .minusDays(1)
            .atTime(9, 0)
            .atZone(org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy.ZONE)
            .toInstant();
    var event =
        new org.grit.daynomy.investmentcalendar.domain.InvestmentEvent(
            org.grit.daynomy.investmentcalendar.domain.InvestmentEventType.CORPORATE_EARNINGS,
            "실적 발표",
            yesterday,
            BigDecimal.ONE,
            BigDecimal.TEN,
            "원",
            "DART",
            "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261018000001",
            "earnings",
            "005930");
    when(events.findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(event));
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).hasSize(1);
    event.synchronize(
        "실적 발표",
        yesterday.plusSeconds(86400),
        null,
        null,
        "원",
        "DART",
        event.getSourceUrl(),
        "005930");
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).isEmpty();
    event.synchronize(
        "실적 발표",
        yesterday,
        BigDecimal.ONE,
        BigDecimal.TEN,
        "원",
        "DART",
        event.getSourceUrl(),
        "005930");
    ReflectionTestUtils.setField(
        korean, "market", org.grit.daynomy.asset.domain.StockMarket.NASDAQ);
    assertThat(service.select(1L, runDate).orElseThrow().stocks()).isEmpty();
  }

  @Test
  void registeredOfficialDisclosureSelectsItsAssetWithoutPriceChanges() {
    savedHolding();
    tradingDates(runDate.minusDays(3), runDate.minusDays(4));
    var korean =
        Asset.listedStock(
            "종목", "005930", org.grit.daynomy.asset.domain.StockMarket.KOSPI, null, runDate);
    ReflectionTestUtils.setField(korean, "id", 10L);
    var content =
        org.grit.daynomy.content.domain.AssetContent.create(
            korean,
            org.grit.daynomy.content.domain.ContentSourceType.OTHER,
            "유상증자결정",
            "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261019000001");
    when(contents.findDisclosureCandidates(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(content));
    assertThat(service.select(1L, runDate).orElseThrow().stocks())
        .extracting(stock -> stock.assetId())
        .containsExactly(10L);
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
