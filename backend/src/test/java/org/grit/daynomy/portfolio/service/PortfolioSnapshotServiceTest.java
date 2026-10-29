package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.dto.PortfolioPricingStatus;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private PortfolioHoldingRepository holdingRepository;
  @Mock private PortfolioDailySnapshotRepository snapshotRepository;
  @Mock private StockDailyPriceRepository stockPriceRepository;
  @Mock private PortfolioSnapshotTransactionService snapshotTransactionService;
  @Mock private PortfolioCalculationService calculationService;
  @InjectMocks private PortfolioSnapshotService service;
  private final Logger logger = (Logger) LoggerFactory.getLogger(PortfolioSnapshotService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureLogs() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void createLatestSnapshotsProcessesEveryDateAndContinuesAfterFailure() {
    LocalDate firstDate = LocalDate.of(2026, 9, 29);
    LocalDate secondDate = LocalDate.of(2026, 9, 30);
    Portfolio firstPortfolio = portfolio(10L);
    Portfolio secondPortfolio = portfolio(20L);
    given(stockPriceRepository.findBaseDatesByPortfolioId(org.mockito.ArgumentMatchers.anyLong()))
        .willReturn(List.of(firstDate, secondDate));
    given(portfolioRepository.findAll()).willReturn(List.of(firstPortfolio, secondPortfolio));
    given(
            historyRepository.findFirstEffectiveDateByPortfolioId(
                org.mockito.ArgumentMatchers.anyLong()))
        .willReturn(Optional.of(firstDate));
    given(snapshotTransactionService.createSnapshot(10L, firstDate, null)).willReturn(true);
    given(snapshotTransactionService.createSnapshot(20L, firstDate, null))
        .willThrow(new ArithmeticException("out of range"));
    given(snapshotTransactionService.createSnapshot(10L, secondDate, firstDate)).willReturn(true);
    given(snapshotTransactionService.createSnapshot(20L, secondDate, firstDate)).willReturn(false);

    int count = service.createLatestSnapshots();

    assertThat(count).isEqualTo(2);
    then(snapshotTransactionService).should().createSnapshot(20L, secondDate, firstDate);
    var failures = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(failures).hasSize(1);
    assertThat(ThrowableProxyUtil.asString(failures.getFirst().getThrowableProxy()))
        .contains("ArithmeticException")
        .doesNotContain("out of range");
  }

  @Test
  void logsPriceSyncSnapshotFailureWithoutRawDatabaseValues() {
    Portfolio portfolio = portfolio(10L);
    given(portfolioRepository.findAll()).willReturn(List.of(portfolio));
    given(stockPriceRepository.findBaseDatesByPortfolioId(10L))
        .willThrow(new IllegalStateException("private-sql-values"));
    service.refreshAfterPriceSync();
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
    assertThat(ThrowableProxyUtil.asString(appender.list.getFirst().getThrowableProxy()))
        .contains("IllegalStateException")
        .doesNotContain("private-sql-values");
  }

  @Test
  void skipsSharedPriceHistoryBeforeFirstHoldingButRechecksExistingSnapshots() {
    LocalDate firstHolding = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(5);
    LocalDate existingDate = firstHolding.minusDays(10);
    Portfolio portfolio = portfolio(10L);
    given(portfolioRepository.findAll()).willReturn(List.of(portfolio));
    given(historyRepository.findFirstEffectiveDateByPortfolioId(10L))
        .willReturn(Optional.of(firstHolding));
    given(stockPriceRepository.findBaseDatesByPortfolioId(10L))
        .willReturn(firstHolding.minusYears(5).datesUntil(firstHolding.plusDays(1)).toList());
    given(snapshotRepository.findBaseDatesByPortfolioId(10L)).willReturn(List.of(existingDate));

    service.createLatestSnapshots();

    then(snapshotTransactionService)
        .should()
        .createSnapshot(10L, existingDate, existingDate.minusDays(1));
    then(snapshotTransactionService)
        .should()
        .createSnapshot(10L, firstHolding, firstHolding.minusDays(1));
    then(snapshotTransactionService).shouldHaveNoMoreInteractions();
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
    assertThat(response.currentPoint()).isNull();
  }

  @Test
  void performanceCalculatesTodayPointFromCurrentHoldingsWithoutCreatingSnapshot() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate priceBaseDate = today.minusDays(1);
    Portfolio portfolio = portfolio(10L);
    PortfolioHolding holding = holding(1L, 2L, "65000.00");
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(
            snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
                10L, today.minusDays(7), today.minusDays(1)))
        .willReturn(List.of());
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of(holding));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 2L, new BigDecimal("65000.00"))));
    given(calculationService.calculate(request))
        .willReturn(
            new PortfolioCalculationResponse(
                priceBaseDate,
                PortfolioPricingStatus.COMPLETE,
                1,
                0,
                new BigDecimal("130000.00"),
                new BigDecimal("140000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("1.45"),
                new BigDecimal("10000.00"),
                new BigDecimal("7.69"),
                List.of(),
                List.of()));

    var response = service.performance(1L, today.minusDays(7), today);

    assertThat(response.currentPoint()).isNotNull();
    assertThat(response.currentPoint().baseDate()).isEqualTo(today);
    assertThat(response.currentPoint().priceBaseDate()).isEqualTo(priceBaseDate);
    assertThat(response.currentPoint().totalPurchaseAmount()).isEqualByComparingTo("130000.00");
    assertThat(response.currentPoint().totalEvaluationAmount()).isEqualByComparingTo("140000.00");
    assertThat(response.currentPoint().dailyProfitLoss()).isNull();
    assertThat(response.currentPoint().dailyReturnRate()).isNull();
    then(calculationService).should().calculate(request);
    then(snapshotTransactionService).shouldHaveNoInteractions();
  }

  @Test
  void performanceReturnsZeroTodayPointWhenPortfolioHasNoHoldings() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    Portfolio portfolio = portfolio(10L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of());

    var response = service.performance(1L, today, today);

    assertThat(response.currentPoint()).isNotNull();
    assertThat(response.currentPoint().baseDate()).isEqualTo(today);
    assertThat(response.currentPoint().totalPurchaseAmount()).isEqualByComparingTo("0.00");
    assertThat(response.currentPoint().totalEvaluationAmount()).isEqualByComparingTo("0.00");
    assertThat(response.currentPoint().totalReturnRate()).isEqualByComparingTo("0.00");
    then(calculationService).shouldHaveNoInteractions();
  }

  @Test
  void performanceReturnsHistoricalSnapshotsWithoutTodayPointWhenPricingIsPartial() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate yesterday = today.minusDays(1);
    Portfolio portfolio = portfolio(10L);
    PortfolioHolding holding = holding(1L, 2L, "65000.00");
    PortfolioDailySnapshot snapshot = mock(PortfolioDailySnapshot.class);
    given(snapshot.getBaseDate()).willReturn(yesterday);
    given(snapshot.getTotalPurchaseAmount()).willReturn(new BigDecimal("130000.00"));
    given(snapshot.getTotalEvaluationAmount()).willReturn(new BigDecimal("140000.00"));
    given(snapshot.getTotalProfitLoss()).willReturn(new BigDecimal("10000.00"));
    given(snapshot.getTotalReturnRate()).willReturn(new BigDecimal("7.69"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(
            snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
                10L, yesterday, yesterday))
        .willReturn(List.of(snapshot));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of(holding));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 2L, new BigDecimal("65000.00"))));
    given(calculationService.calculate(request))
        .willReturn(
            new PortfolioCalculationResponse(
                null,
                PortfolioPricingStatus.PARTIAL,
                0,
                1,
                new BigDecimal("130000.00"),
                BigDecimal.ZERO.setScale(2),
                null,
                null,
                null,
                null,
                List.of(),
                List.of()));

    var response = service.performance(1L, yesterday, today);

    assertThat(response.points()).hasSize(1);
    assertThat(response.currentPoint()).isNull();
  }

  @Test
  void performancePropagatesUnexpectedCalculationBusinessException() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    Portfolio portfolio = portfolio(10L);
    PortfolioHolding holding = holding(1L, 2L, "65000.00");
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of(holding));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 2L, new BigDecimal("65000.00"))));
    given(calculationService.calculate(request))
        .willThrow(new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET));

    assertThatThrownBy(() -> service.performance(1L, today, today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
  }

  @Test
  void createLatestSnapshotsDoesNotPersistTodayPoint() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate yesterday = today.minusDays(1);
    Portfolio portfolio = portfolio(10L);
    given(stockPriceRepository.findBaseDatesByPortfolioId(org.mockito.ArgumentMatchers.anyLong()))
        .willReturn(List.of(yesterday, today));
    given(portfolioRepository.findAll()).willReturn(List.of(portfolio));
    given(historyRepository.findFirstEffectiveDateByPortfolioId(10L))
        .willReturn(Optional.of(yesterday));
    given(snapshotTransactionService.createSnapshot(10L, yesterday, null)).willReturn(true);

    int count = service.createLatestSnapshots();

    assertThat(count).isEqualTo(1);
    then(snapshotTransactionService).should().createSnapshot(10L, yesterday, null);
    then(snapshotTransactionService).shouldHaveNoMoreInteractions();
  }

  private Portfolio portfolio(Long id) {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(id);
    return portfolio;
  }

  private PortfolioHolding holding(Long assetId, long quantity, String averagePurchasePrice) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(assetId);
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(quantity);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal(averagePurchasePrice));
    return holding;
  }
}
