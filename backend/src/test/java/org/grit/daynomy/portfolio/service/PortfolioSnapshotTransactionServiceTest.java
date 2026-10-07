package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotTransactionServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private PortfolioDailySnapshotRepository snapshotRepository;
  @Mock private StockDailyPriceRepository stockPriceRepository;
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
    given(portfolioRepository.findById(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.empty());
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(10L), any()))
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
  void createSnapshotDoesNotOverwritePastSnapshot() {
    LocalDate baseDate = LocalDate.now().minusDays(1);
    Portfolio portfolio = mock(Portfolio.class);
    PortfolioDailySnapshot existing = mock(PortfolioDailySnapshot.class);
    given(portfolioRepository.findById(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.of(existing));

    boolean created = service.createSnapshot(10L, baseDate, baseDate.minusDays(1));

    assertThat(created).isFalse();
    then(historyRepository).shouldHaveNoInteractions();
    then(snapshotRepository).should(never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
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
    given(portfolioRepository.findById(10L)).willReturn(Optional.of(portfolio));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.empty());
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(10L), any()))
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
