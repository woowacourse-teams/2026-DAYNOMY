package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioDailySnapshotRepository snapshotRepository;
  @Mock private StockDailyPriceRepository stockPriceRepository;
  @Mock private PortfolioSnapshotTransactionService snapshotTransactionService;
  @InjectMocks private PortfolioSnapshotService service;

  @Test
  void createLatestSnapshotsProcessesEveryDateAndContinuesAfterFailure() {
    LocalDate firstDate = LocalDate.of(2026, 9, 29);
    LocalDate secondDate = LocalDate.of(2026, 9, 30);
    Portfolio firstPortfolio = portfolio(10L);
    Portfolio secondPortfolio = portfolio(20L);
    given(stockPriceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(firstDate, secondDate));
    given(portfolioRepository.findAll()).willReturn(List.of(firstPortfolio, secondPortfolio));
    given(snapshotTransactionService.createSnapshot(10L, firstDate, null)).willReturn(true);
    given(snapshotTransactionService.createSnapshot(20L, firstDate, null))
        .willThrow(new ArithmeticException("out of range"));
    given(snapshotTransactionService.createSnapshot(10L, secondDate, firstDate)).willReturn(true);
    given(snapshotTransactionService.createSnapshot(20L, secondDate, firstDate)).willReturn(false);

    int count = service.createLatestSnapshots();

    assertThat(count).isEqualTo(2);
    then(snapshotTransactionService).should().createSnapshot(20L, secondDate, firstDate);
  }

  @Test
  void performanceReturnsInsufficientDataWithOneSnapshot() {
    LocalDate baseDate = LocalDate.of(2026, 9, 30);
    Portfolio portfolio = portfolio(10L);
    PortfolioDailySnapshot snapshot = mock(PortfolioDailySnapshot.class);
    given(snapshot.getBaseDate()).willReturn(baseDate);
    given(snapshot.getTotalPurchaseAmount()).willReturn(new BigDecimal("20000.00"));
    given(snapshot.getTotalEvaluationAmount()).willReturn(new BigDecimal("24000.00"));
    given(snapshot.getTotalProfitLoss()).willReturn(new BigDecimal("4000.00"));
    given(snapshot.getTotalReturnRate()).willReturn(new BigDecimal("20.00"));
    given(snapshot.getDailyProfitLoss()).willReturn(null);
    given(snapshot.getDailyReturnRate()).willReturn(null);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(
            snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
                10L, baseDate, baseDate))
        .willReturn(List.of(snapshot));

    var response = service.performance(1L, baseDate, baseDate);

    assertThat(response.status()).isEqualTo(PortfolioPerformanceStatus.INSUFFICIENT_DATA);
    assertThat(response.reason())
        .isEqualTo(PortfolioPerformanceUnavailableReason.SNAPSHOT_DATA_INSUFFICIENT);
    assertThat(response.points()).hasSize(1);
  }

  private Portfolio portfolio(Long id) {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(id);
    return portfolio;
  }
}
