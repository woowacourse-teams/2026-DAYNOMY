package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingRepository holdingRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private PortfolioLedgerCalculator ledgerCalculator;
  @Mock private PortfolioSnapshotTransactionService snapshotTransactionService;
  @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Mock private org.grit.daynomy.portfolio.service.PortfolioExchangeRateService exchangeRates;
  @InjectMocks private SavedPortfolioService service;

  @ParameterizedTest
  @CsvSource({"5,10000", "3,11000", "5,11000"})
  void updateHoldingRecordsUpdatedHistory(long quantity, String price) {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    PortfolioHolding holding = new PortfolioHolding(portfolio, asset, 3L, new BigDecimal("10000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));

    service.update(1L, 2L, new SavedPortfolioHoldingUpdateRequest(quantity, new BigDecimal(price)));

    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType())
        .isEqualTo(PortfolioHoldingChangeType.ADJUSTED);
    assertThat(historyCaptor.getValue().getQuantity()).isEqualTo(quantity);
    assertThat(historyCaptor.getValue().getAveragePurchasePrice()).isEqualByComparingTo(price);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10000", "10000.00"})
  void unchangedHoldingDoesNotCreateHistory(String price) {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    PortfolioHolding holding =
        new PortfolioHolding(portfolio, asset(), 3L, new BigDecimal("10000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));

    assertThat(
            service
                .update(1L, 2L, new SavedPortfolioHoldingUpdateRequest(3L, new BigDecimal(price)))
                .quantity())
        .isEqualTo(3L);

    then(historyRepository).shouldHaveNoInteractions();
  }

  @Test
  void removeHoldingRecordsRemovedHistory() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = mock(Asset.class);
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(3L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("10000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));

    service.remove(1L, 2L);

    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType())
        .isEqualTo(PortfolioHoldingChangeType.REMOVED);
    then(holdingRepository).should().delete(holding);
  }

  @Test
  void sellTransactionUpdatesHoldingAndReturnsRealizedProfitLoss() {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(10L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("70000"));
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(assetRepository.findById(2L)).willReturn(Optional.of(asset));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));
    given(historyRepository.saveAndFlush(any(PortfolioHoldingHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L))
        .willReturn(List.of());
    given(ledgerCalculator.replay(List.of()))
        .willReturn(
            new PortfolioLedgerCalculator.LedgerResult(
                Map.of(
                    2L,
                    new PortfolioLedgerCalculator.Position(asset, 8L, new BigDecimal("70000.00"))),
                new BigDecimal("19700.00")));
    var request =
        new PortfolioTransactionRequest(
            2L,
            PortfolioTransactionType.SELL,
            LocalDate.of(2026, 9, 30),
            2L,
            new BigDecimal("80000"),
            new BigDecimal("100"),
            new BigDecimal("200"));

    var response = service.transact(1L, request);

    then(holding).should().change(8L, new BigDecimal("70000.00"), null);
    assertThat(response.totalRealizedProfitLoss()).isEqualByComparingTo("19700.00");
    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().saveAndFlush(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType()).isEqualTo(PortfolioHoldingChangeType.SELL);
    assertThat(historyCaptor.getValue().getEffectiveDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(historyCaptor.getValue().getTransactionQuantity()).isEqualTo(2L);
    assertThat(historyCaptor.getValue().getFee()).isEqualByComparingTo("100");
    assertThat(historyCaptor.getValue().getTax()).isEqualByComparingTo("200");
    then(snapshotTransactionService)
        .should()
        .refreshSnapshotsFrom(10L, LocalDate.of(2026, 9, 30), java.util.Map.of());
  }

  @Test
  void updateTransactionRecalculatesHolding() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    PortfolioHolding holding = mock(PortfolioHolding.class);
    PortfolioHoldingHistory history =
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.BUY,
            0L,
            BigDecimal.ZERO,
            LocalDate.of(2026, 9, 30),
            2L,
            new BigDecimal("70000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(history));
    given(historyRepository.saveAndFlush(history)).willReturn(history);
    given(historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L))
        .willReturn(List.of(history));
    given(ledgerCalculator.replay(List.of(history)))
        .willReturn(
            new PortfolioLedgerCalculator.LedgerResult(
                Map.of(
                    2L,
                    new PortfolioLedgerCalculator.Position(asset, 3L, new BigDecimal("81000.00"))),
                BigDecimal.ZERO));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(3L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("81000.00"));
    var request =
        new PortfolioTransactionUpdateRequest(
            LocalDate.of(2026, 10, 1),
            3L,
            new BigDecimal("81000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    service.updateTransaction(1L, 7L, request);

    assertThat(history.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(history.getTransactionQuantity()).isEqualTo(3L);
    assertThat(history.getUnitPrice()).isEqualByComparingTo("81000");
    then(holding).should().change(3L, new BigDecimal("81000.00"), null);
    then(snapshotTransactionService)
        .should()
        .refreshSnapshotsFrom(10L, LocalDate.of(2026, 9, 30), java.util.Map.of());
  }

  @Test
  void updateTransactionRejectsTransactionFromAnotherPortfolio() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.empty());
    var request = transactionUpdateRequest(3L);

    assertThatThrownBy(() -> service.updateTransaction(1L, 7L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_TRANSACTION_NOT_FOUND);
  }

  @Test
  void updateTransactionRejectsOpeningBalanceHistory() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    PortfolioHoldingHistory openingBalance =
        new PortfolioHoldingHistory(
            portfolio,
            mock(Asset.class),
            PortfolioHoldingChangeType.OPENING_BALANCE,
            3L,
            new BigDecimal("70000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L))
        .willReturn(Optional.of(openingBalance));

    assertThatThrownBy(() -> service.updateTransaction(1L, 7L, transactionUpdateRequest(3L)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_TRANSACTION_NOT_FOUND);
  }

  @Test
  void updateTransactionRejectsLedgerThatWouldOversell() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    PortfolioHoldingHistory history = transactionHistory(portfolio, PortfolioHoldingChangeType.BUY);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(history));
    given(historyRepository.saveAndFlush(history)).willReturn(history);
    given(historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L))
        .willReturn(List.of(history));
    given(ledgerCalculator.replay(List.of(history)))
        .willThrow(new BusinessException(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY));

    assertThatThrownBy(() -> service.updateTransaction(1L, 7L, transactionUpdateRequest(1L)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY);
    then(snapshotTransactionService).shouldHaveNoInteractions();
  }

  @Test
  void deleteTransactionRecalculatesHolding() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    PortfolioHolding holding = mock(PortfolioHolding.class);
    PortfolioHoldingHistory history =
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.SELL,
            5L,
            new BigDecimal("70000"),
            LocalDate.of(2026, 9, 30),
            2L,
            new BigDecimal("80000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(history));
    given(historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L))
        .willReturn(List.of());
    given(ledgerCalculator.replay(List.of()))
        .willReturn(
            new PortfolioLedgerCalculator.LedgerResult(
                Map.of(
                    2L,
                    new PortfolioLedgerCalculator.Position(asset, 5L, new BigDecimal("70000.00"))),
                BigDecimal.ZERO));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(5L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("70000.00"));

    var response = service.deleteTransaction(1L, 7L);

    then(historyRepository).should().delete(history);
    then(historyRepository).should().flush();
    then(holding).should().change(5L, new BigDecimal("70000.00"), null);
    assertThat(response.transactionId()).isEqualTo(7L);
    assertThat(response.totalRealizedProfitLoss()).isEqualByComparingTo("0");
    then(snapshotTransactionService)
        .should()
        .refreshSnapshotsFrom(10L, LocalDate.of(2026, 9, 30), java.util.Map.of());
  }

  @Test
  void deleteTransactionRejectsLedgerThatWouldOversell() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    PortfolioHoldingHistory buy = transactionHistory(portfolio, PortfolioHoldingChangeType.BUY);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(buy));
    given(historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L))
        .willReturn(List.of());
    given(ledgerCalculator.replay(List.of()))
        .willThrow(new BusinessException(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY));

    assertThatThrownBy(() -> service.deleteTransaction(1L, 7L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY);
    then(snapshotTransactionService).shouldHaveNoInteractions();
  }

  @Test
  void historiesUsesExclusiveNextDayBoundary() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    LocalDate date = LocalDate.of(2026, 9, 30);

    service.histories(1L, date, date);

    then(historyRepository)
        .should()
        .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
            10L, date, date);
  }

  private Asset asset() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(2L);
    given(asset.getAssetCode()).willReturn("005930");
    given(asset.getName()).willReturn("삼성전자");
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    given(asset.getMarket()).willReturn(StockMarket.KOSPI);
    return asset;
  }

  private PortfolioTransactionUpdateRequest transactionUpdateRequest(long quantity) {
    return new PortfolioTransactionUpdateRequest(
        LocalDate.of(2026, 10, 1),
        quantity,
        new BigDecimal("81000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private PortfolioHoldingHistory transactionHistory(
      Portfolio portfolio, PortfolioHoldingChangeType changeType) {
    return new PortfolioHoldingHistory(
        portfolio,
        mock(Asset.class),
        changeType,
        0L,
        BigDecimal.ZERO,
        LocalDate.of(2026, 9, 30),
        2L,
        new BigDecimal("70000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  @Test
  void rejectsUsdTradeWithoutExchangeRateBeforeWritingLedger() {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    Asset apple =
        Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, LocalDate.of(2026, 10, 16));
    given(assetRepository.findById(2L)).willReturn(Optional.of(apple));
    var request =
        new PortfolioTransactionRequest(
            2L,
            PortfolioTransactionType.BUY,
            LocalDate.of(2026, 10, 16),
            1L,
            new BigDecimal("245"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    assertThatThrownBy(() -> service.transact(1L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(PortfolioErrorCode.INVALID_TRANSACTION_EXCHANGE_RATE));
    org.mockito.Mockito.verifyNoInteractions(
        historyRepository, holdingRepository, snapshotTransactionService);
  }
}
