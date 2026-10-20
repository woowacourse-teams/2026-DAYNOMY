package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient.HistoricalPrice;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PortfolioPriceHistoryServiceTest {
  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private PortfolioLedgerCalculator ledgerCalculator;
  @Mock private AssetRepository assetRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private InvestmentCalendarHistoricalPriceClient domesticClient;
  @Mock private EodhdClient usClient;
  @Mock private KrxCalendarClient krxCalendarClient;
  @Mock private PortfolioSnapshotTransactionService snapshotService;
  @Mock private TaskScheduler taskScheduler;
  @Mock private PlatformTransactionManager transactionManager;
  @InjectMocks private PortfolioPriceHistoryService service;

  private final LocalDate from =
      LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(1).withDayOfMonth(8);
  private final LocalDate to = from.plusDays(4);
  private Asset asset;

  @BeforeEach
  void setUp() {
    asset =
        Asset.listedSecurity("Apple", AssetCategory.STOCK, "AAPL", StockMarket.NASDAQ, null, from);
    ReflectionTestUtils.setField(asset, "id", 7L);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(3L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    PortfolioHoldingHistory history = mock(PortfolioHoldingHistory.class);
    given(history.getEffectiveDate()).willReturn(from);
    given(historyRepository.findOrderedWithAssetsByPortfolioId(3L)).willReturn(List.of(history));
    given(assetRepository.findById(7L)).willReturn(Optional.of(asset));
    given(ledgerCalculator.replay(any()))
        .willReturn(
            new PortfolioLedgerCalculator.LedgerResult(
                Map.of(7L, new PortfolioLedgerCalculator.Position(asset, 1, BigDecimal.TEN)),
                BigDecimal.ZERO));
  }

  @Test
  void reusesCoveredPricesWithoutCallingProvider() {
    asset.recordPriceHistoryCoverage(from, to);
    given(priceRepository.findBaseDatesByPortfolioId(3L)).willReturn(List.of(from));
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();
    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.READY);

    then(usClient).shouldHaveNoInteractions();
  }

  @Test
  void doesNotTreatAnUnrefreshedGapBetweenDateRangesAsReady() {
    asset.recordPriceHistoryCoverage(from, to);
    given(priceRepository.findBaseDatesByPortfolioId(3L)).willReturn(List.of(from, to));
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, from, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(jobs.capture(), any(java.time.Instant.class));
    jobs.getValue().run();
    assertThat(service.prepare(2L, to, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    then(taskScheduler)
        .should(org.mockito.Mockito.times(2))
        .schedule(jobs.capture(), any(java.time.Instant.class));
    jobs.getAllValues().getLast().run();

    assertThat(service.prepare(2L, from.plusDays(2), from.plusDays(2), false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
  }

  @Test
  void fetchesMissingUsHistoryThenRefreshesSnapshots() {
    given(usClient.getUsMarketHolidays(from, to)).willReturn(Set.of());
    given(usClient.getHistoricalPrices("AAPL", from, to))
        .willReturn(
            weekdays(from, to).stream()
                .map(date -> new EodhdDailyPrice("AAPL", "US", date, new BigDecimal("271.1234")))
                .toList());
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(priceRepository.findBaseDatesByPortfolioId(3L)).willReturn(List.of(from));
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    then(usClient).should().getHistoricalPrices("AAPL", from, to);
    then(priceRepository).should().saveAll(any());
    then(snapshotService).should().createSnapshot(3L, from, null);
    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.READY);
  }

  @Test
  void reusesStoredPricesWhenCoverageWasNotRecordedYet() {
    List<StockDailyPrice> stored =
        weekdays(from, to).stream()
            .map(
                date -> {
                  StockDailyPrice price = mock(StockDailyPrice.class);
                  given(price.getBaseDate()).willReturn(date);
                  return price;
                })
            .toList();
    given(usClient.getUsMarketHolidays(from, to)).willReturn(Set.of());
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                List.of(7L), from, to))
        .willReturn(stored);
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(asset.hasPriceHistory(from, to)).isTrue();
    then(usClient).should().getUsMarketHolidays(from, to);
    then(usClient).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchesMissingDomesticHistoryThroughDomesticProvider() {
    ReflectionTestUtils.setField(asset, "market", StockMarket.KOSPI);
    given(krxCalendarClient.holidays(from.getYear())).willReturn(Set.of());
    given(domesticClient.fetch(AssetCategory.STOCK, "AAPL", from, to))
        .willReturn(
            weekdays(from, to).stream()
                .map(date -> new HistoricalPrice(date, new BigDecimal("70000")))
                .toList());
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(priceRepository.findBaseDatesByPortfolioId(3L)).willReturn(List.of(from));
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    then(domesticClient).should().fetch(AssetCategory.STOCK, "AAPL", from, to);
    then(usClient).shouldHaveNoInteractions();
    then(snapshotService).should().createSnapshot(3L, from, null);
  }

  @Test
  void reportsFailedCollectionUntilExplicitRetry() {
    given(usClient.getUsMarketHolidays(from, to)).willReturn(Set.of());
    given(usClient.getHistoricalPrices("AAPL", from, to))
        .willThrow(new IllegalStateException("provider unavailable"));
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.FAILED);
    assertThat(service.prepare(2L, from, to, true))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
  }

  @Test
  void weekendOnlyRangeExtendsCoverageWithoutRequestingPrices() {
    LocalDate saturday =
        LocalDate.now(ZoneId.of("Asia/Seoul"))
            .minusDays(1)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    LocalDate sunday = saturday.plusDays(1);
    asset.recordPriceHistoryCoverage(saturday.minusDays(1), saturday.minusDays(1));
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, saturday, sunday, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(asset.hasPriceHistory(saturday, saturday)).isTrue();
    then(usClient).shouldHaveNoInteractions();
    assertThat(service.prepare(2L, saturday, sunday, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.READY);
  }

  @Test
  void domesticHolidayRangeDoesNotRequestPrices() {
    LocalDate friday =
        LocalDate.now(ZoneId.of("Asia/Seoul"))
            .minusDays(1)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    ReflectionTestUtils.setField(asset, "market", StockMarket.KOSPI);
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(friday, friday))
        .willReturn(List.of(new MarketTradingDay(friday, false)));
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, friday, friday, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(asset.hasPriceHistory(friday, friday)).isTrue();
    then(domesticClient).shouldHaveNoInteractions();
    then(krxCalendarClient).shouldHaveNoInteractions();
  }

  @Test
  void usHolidayRangeDoesNotRequestPrices() {
    LocalDate friday =
        LocalDate.now(ZoneId.of("Asia/Seoul"))
            .minusDays(1)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    given(usClient.getUsMarketHolidays(friday, friday)).willReturn(Set.of(friday));
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, friday, friday, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(asset.hasPriceHistory(friday, friday)).isTrue();
    then(usClient).should().getUsMarketHolidays(friday, friday);
    then(usClient).shouldHaveNoMoreInteractions();
  }

  @Test
  void missingTradingDayPriceDoesNotMarkWholeRangeCovered() {
    List<LocalDate> expected = weekdays(from, to);
    given(usClient.getUsMarketHolidays(from, to)).willReturn(Set.of());
    given(usClient.getHistoricalPrices("AAPL", from, to))
        .willReturn(
            List.of(
                new EodhdDailyPrice(
                    "AAPL", "US", expected.getFirst(), new BigDecimal("271.1234"))));
    given(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());
    given(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
        .willReturn(mock(ScheduledFuture.class));

    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.PROCESSING);
    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    then(taskScheduler).should().schedule(job.capture(), any(java.time.Instant.class));
    job.getValue().run();

    assertThat(asset.getPortfolioPriceHistoryThrough()).isEqualTo(expected.get(1).minusDays(1));
    assertThat(asset.hasPriceHistory(from, to)).isFalse();
    assertThat(service.prepare(2L, from, to, false))
        .isEqualTo(PortfolioPriceHistoryService.Status.FAILED);
  }

  private List<LocalDate> weekdays(LocalDate start, LocalDate end) {
    return start
        .datesUntil(end.plusDays(1))
        .filter(date -> date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue())
        .toList();
  }
}
