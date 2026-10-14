package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotTransactionServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private PortfolioDailySnapshotRepository snapshotRepository;
  @Mock private StockDailyPriceRepository stockPriceRepository;
  @Spy private PortfolioLedgerCalculator ledgerCalculator = new PortfolioLedgerCalculator();
  @InjectMocks private PortfolioSnapshotTransactionService service;

  @Test
  void createSnapshotReplaysHistoryAndCalculatesDailyAndTotalReturns() {
    LocalDate previousDate = LocalDate.of(2026, 9, 29);
    LocalDate baseDate = LocalDate.of(2026, 9, 30);
    Portfolio portfolio = mock(Portfolio.class);
    Asset retainedAsset = asset(2L);
    Asset removedAsset = asset(3L);
    StockDailyPrice currentPrice = price("12000");
    StockDailyPrice previousPrice = price("11000");
    List<PortfolioHoldingHistory> histories =
        List.of(
            history(retainedAsset, PortfolioHoldingChangeType.ADDED, 1L, "9000"),
            history(retainedAsset, PortfolioHoldingChangeType.UPDATED, 2L, "10000"),
            history(removedAsset, PortfolioHoldingChangeType.ADDED, 3L, "5000"),
            history(removedAsset, PortfolioHoldingChangeType.REMOVED, 3L, "5000"));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.empty());
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    eq(10L), eq(baseDate)))
        .willReturn(histories);
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, baseDate))
        .willReturn(Optional.of(currentPrice));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, previousDate))
        .willReturn(Optional.of(previousPrice));

    boolean created = service.createSnapshot(10L, baseDate, previousDate);

    assertThat(created).isTrue();
    ArgumentCaptor<PortfolioDailySnapshot> captor =
        ArgumentCaptor.forClass(PortfolioDailySnapshot.class);
    then(snapshotRepository).should().saveAndFlush(captor.capture());
    PortfolioDailySnapshot snapshot = captor.getValue();
    assertThat(snapshot.getTotalPurchaseAmount()).isEqualByComparingTo("20000.00");
    assertThat(snapshot.getTotalEvaluationAmount()).isEqualByComparingTo("24000.00");
    assertThat(snapshot.getTotalProfitLoss()).isEqualByComparingTo("4000.00");
    assertThat(snapshot.getTotalReturnRate()).isEqualByComparingTo("20.00");
    assertThat(snapshot.getDailyProfitLoss()).isEqualByComparingTo("2000.00");
    assertThat(snapshot.getDailyReturnRate()).isEqualByComparingTo("9.09");
    then(stockPriceRepository).should(never()).findByAssetIdAndBaseDate(3L, baseDate);
  }

  @Test
  void createSnapshotRemovesPastSnapshotWhenClosingPriceIsMissing() {
    LocalDate baseDate = LocalDate.now().minusDays(1);
    Portfolio portfolio = mock(Portfolio.class);
    PortfolioDailySnapshot existing = mock(PortfolioDailySnapshot.class);
    Asset asset = asset(2L);
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.of(existing));
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    10L, baseDate))
        .willReturn(List.of(history(asset, PortfolioHoldingChangeType.ADDED, 2L, "10000")));

    assertThat(service.createSnapshot(10L, baseDate, baseDate.minusDays(1))).isFalse();

    then(snapshotRepository).should().delete(existing);
    then(snapshotRepository).should(never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void createSnapshotDoesNotWriteAgainWhenAmountsAreUnchanged() {
    LocalDate baseDate = LocalDate.now().minusDays(1);
    Portfolio portfolio = mock(Portfolio.class);
    Asset asset = asset(2L);
    PortfolioDailySnapshot existing = new PortfolioDailySnapshot(portfolio, baseDate);
    existing.update(
        new BigDecimal("20000.00"),
        new BigDecimal("24000.00"),
        new BigDecimal("4000.00"),
        new BigDecimal("20.00"),
        null,
        null);
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.of(existing));
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    10L, baseDate))
        .willReturn(List.of(history(asset, PortfolioHoldingChangeType.ADDED, 2L, "10000")));
    StockDailyPrice currentPrice = price("12000");
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, baseDate))
        .willReturn(Optional.of(currentPrice));

    assertThat(service.createSnapshot(10L, baseDate, null)).isFalse();

    then(snapshotRepository).should(never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void createSnapshotDoesNotPersistTodayOrFuture() {
    assertThat(service.createSnapshot(10L, LocalDate.now().plusDays(1), null)).isFalse();
    then(portfolioRepository).shouldHaveNoInteractions();
    then(snapshotRepository).shouldHaveNoInteractions();
  }

  @Test
  void refreshSnapshotsFromOverwritesPastSnapshot() {
    LocalDate previousDate = LocalDate.now().minusDays(2);
    LocalDate baseDate = LocalDate.now().minusDays(1);
    Portfolio portfolio = mock(Portfolio.class);
    PortfolioDailySnapshot existing = new PortfolioDailySnapshot(portfolio, baseDate);
    Asset asset = asset(2L);
    StockDailyPrice currentPrice = price("12000");
    StockDailyPrice previousPrice = price("11000");
    PortfolioHoldingHistory history = history(asset, PortfolioHoldingChangeType.ADDED, 2L, "10000");
    given(stockPriceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(previousDate, baseDate));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.of(existing));
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    10L, baseDate))
        .willReturn(List.of(history));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, baseDate))
        .willReturn(Optional.of(currentPrice));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, previousDate))
        .willReturn(Optional.of(previousPrice));

    service.refreshSnapshotsFrom(10L, baseDate);

    assertThat(existing.getTotalPurchaseAmount()).isEqualByComparingTo("20000.00");
    assertThat(existing.getTotalEvaluationAmount()).isEqualByComparingTo("24000.00");
    assertThat(existing.getDailyReturnRate()).isEqualByComparingTo("9.09");
    then(snapshotRepository).should().saveAndFlush(existing);
  }

  @Test
  void refreshSnapshotsFromRemovesStaleSnapshotsAcrossAffectedDates() {
    LocalDate beforeDate = LocalDate.now().minusDays(3);
    LocalDate fromDate = LocalDate.now().minusDays(2);
    LocalDate afterDate = LocalDate.now().minusDays(1);
    Portfolio portfolio = mock(Portfolio.class);
    PortfolioDailySnapshot fromSnapshot = mock(PortfolioDailySnapshot.class);
    PortfolioDailySnapshot afterSnapshot = mock(PortfolioDailySnapshot.class);
    given(stockPriceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(beforeDate, fromDate, afterDate));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, fromDate))
        .willReturn(Optional.of(fromSnapshot));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, afterDate))
        .willReturn(Optional.of(afterSnapshot));
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    10L, fromDate))
        .willReturn(List.of());
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    10L, afterDate))
        .willReturn(List.of());

    service.refreshSnapshotsFrom(10L, fromDate);

    then(snapshotRepository).should(never()).findByPortfolioIdAndBaseDate(10L, beforeDate);
    then(snapshotRepository).should().delete(fromSnapshot);
    then(snapshotRepository).should().delete(afterSnapshot);
  }

  @Test
  void createSnapshotWaitsUntilPreviousClosingPriceIsAvailable() {
    LocalDate previousDate = LocalDate.of(2026, 9, 29);
    LocalDate baseDate = LocalDate.of(2026, 9, 30);
    Portfolio portfolio = mock(Portfolio.class);
    Asset asset = asset(2L);
    StockDailyPrice currentPrice = price("12000");
    PortfolioHoldingHistory addedHistory =
        history(asset, PortfolioHoldingChangeType.ADDED, 2L, "10000");
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.empty());
    given(
            historyRepository
                .findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
                    eq(10L), eq(baseDate)))
        .willReturn(List.of(addedHistory));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, baseDate))
        .willReturn(Optional.of(currentPrice));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, previousDate))
        .willReturn(Optional.empty());

    boolean created = service.createSnapshot(10L, baseDate, previousDate);

    assertThat(created).isFalse();
    then(snapshotRepository).should(never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  private Asset asset(Long id) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    return asset;
  }

  private PortfolioHoldingHistory history(
      Asset asset, PortfolioHoldingChangeType type, long quantity, String averagePrice) {
    return new PortfolioHoldingHistory(
        new PortfolioHolding(mock(Portfolio.class), asset, quantity, new BigDecimal(averagePrice)),
        type);
  }

  private StockDailyPrice price(String closePrice) {
    StockDailyPrice price = mock(StockDailyPrice.class);
    given(price.getClosePrice()).willReturn(new BigDecimal(closePrice));
    return price;
  }
}
