package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioTransactionDateTest {

  private static final LocalDate BALANCE_DATE = LocalDate.of(2026, 10, 1);
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

  private Portfolio portfolio;
  private Asset asset;

  @BeforeEach
  void setUp() {
    portfolio = mock(Portfolio.class);
    asset = mock(Asset.class);
    given(portfolio.getId()).willReturn(10L);
    given(asset.getId()).willReturn(2L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
  }

  @ParameterizedTest
  @CsvSource({
    "OPENING_BALANCE, BUY", "OPENING_BALANCE, SELL", "ADJUSTED, BUY", "ADJUSTED, SELL",
    "ADDED, BUY", "ADDED, SELL", "UPDATED, BUY", "UPDATED, SELL"
  })
  void transactRejectsTradeBeforeSameAssetBalance(
      PortfolioHoldingChangeType balanceType, PortfolioTransactionType type) {
    prepareRegistration();
    given(histories()).willReturn(List.of(history(asset, balanceType, BALANCE_DATE)));

    assertThatThrownBy(() -> service.transact(1L, request(type, BALANCE_DATE.minusDays(1))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INVALID_PORTFOLIO_TRADE_DATE);

    then(historyRepository).should(never()).saveAndFlush(any());
    then(holdingRepository).shouldHaveNoInteractions();
    then(ledgerCalculator).shouldHaveNoInteractions();
    then(snapshotTransactionService).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @CsvSource({
    "OPENING_BALANCE, BUY", "OPENING_BALANCE, SELL", "ADJUSTED, BUY", "ADJUSTED, SELL",
    "ADDED, BUY", "ADDED, SELL", "UPDATED, BUY", "UPDATED, SELL"
  })
  void updateRejectsEarlierTradeDateWithoutChangingTransaction(
      PortfolioHoldingChangeType balanceType, PortfolioHoldingChangeType transactionType) {
    PortfolioHoldingHistory transaction = history(asset, transactionType, BALANCE_DATE.plusDays(1));
    given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(transaction));
    given(histories()).willReturn(List.of(history(asset, balanceType, BALANCE_DATE), transaction));

    assertThatThrownBy(
            () -> service.updateTransaction(1L, 7L, updateRequest(BALANCE_DATE.minusDays(1))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INVALID_PORTFOLIO_TRADE_DATE);

    assertThat(transaction.getEffectiveDate()).isEqualTo(BALANCE_DATE.plusDays(1));
    assertThat(transaction.getTransactionQuantity()).isEqualTo(5L);
    then(historyRepository).should(never()).saveAndFlush(any());
    then(holdingRepository).shouldHaveNoInteractions();
    then(ledgerCalculator).shouldHaveNoInteractions();
    then(snapshotTransactionService).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @CsvSource({"false, 0", "false, 1", "true, 0", "true, 1"})
  void acceptsTradeOnOrAfterBalanceDate(boolean update, int daysAfter) {
    given(histories())
        .willReturn(List.of(history(asset, PortfolioHoldingChangeType.ADJUSTED, BALANCE_DATE)));
    executeAcceptedTrade(update, BALANCE_DATE.plusDays(daysAfter));
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void laterBalanceForAnotherAssetDoesNotBlockTrade(boolean update) {
    Asset otherAsset = mock(Asset.class);
    given(otherAsset.getId()).willReturn(3L);
    given(histories())
        .willReturn(
            List.of(history(otherAsset, PortfolioHoldingChangeType.OPENING_BALANCE, BALANCE_DATE)));
    executeAcceptedTrade(update, BALANCE_DATE.minusDays(1));
  }

  private void executeAcceptedTrade(boolean update, LocalDate tradeDate) {
    given(ledgerCalculator.replay(any()))
        .willReturn(new PortfolioLedgerCalculator.LedgerResult(Map.of(), BigDecimal.ZERO));
    given(historyRepository.saveAndFlush(any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    if (update) {
      PortfolioHoldingHistory transaction =
          history(asset, PortfolioHoldingChangeType.BUY, tradeDate);
      given(historyRepository.findByIdAndPortfolioId(7L, 10L)).willReturn(Optional.of(transaction));
      service.updateTransaction(1L, 7L, updateRequest(tradeDate));
    } else {
      prepareRegistration();
      service.transact(1L, request(PortfolioTransactionType.BUY, tradeDate));
    }
    then(historyRepository).should().saveAndFlush(any());
    then(snapshotTransactionService)
        .should()
        .refreshSnapshotsFrom(10L, tradeDate, java.util.Map.of());
  }

  private void prepareRegistration() {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(assetRepository.findById(2L)).willReturn(Optional.of(asset));
  }

  private List<PortfolioHoldingHistory> histories() {
    return historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(10L);
  }

  private PortfolioHoldingHistory history(
      Asset target, PortfolioHoldingChangeType type, LocalDate date) {
    return new PortfolioHoldingHistory(
        portfolio,
        target,
        type,
        10L,
        new BigDecimal("10000"),
        date,
        5L,
        new BigDecimal("10000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private PortfolioTransactionRequest request(PortfolioTransactionType type, LocalDate date) {
    return new PortfolioTransactionRequest(
        2L, type, date, 5L, new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO);
  }

  private PortfolioTransactionUpdateRequest updateRequest(LocalDate date) {
    return new PortfolioTransactionUpdateRequest(
        date, 3L, new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO);
  }
}
