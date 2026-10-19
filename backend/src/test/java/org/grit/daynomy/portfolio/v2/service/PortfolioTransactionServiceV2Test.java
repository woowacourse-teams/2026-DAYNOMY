package org.grit.daynomy.portfolio.v2.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.*;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioTransactionServiceV2Test {
  @Mock MemberRepository memberRepository;
  @Mock PortfolioRepository portfolioRepository;
  @Mock AssetRepository assetRepository;
  @Mock PortfolioHoldingRepository holdingRepository;
  @Mock PortfolioHoldingHistoryRepository historyRepository;
  @Mock PortfolioLedgerCalculator ledgerCalculator;
  @Mock PortfolioSnapshotServiceV2 snapshotService;
  @InjectMocks PortfolioTransactionServiceV2 service;

  @Test
  void rejectsUsdTradeWithoutExchangeRateBeforeWritingLedger() {
    Member member = mock(Member.class);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolioRepository.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    var date = LocalDate.of(2026, 10, 16);
    given(assetRepository.findById(2L))
        .willReturn(
            Optional.of(Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, date)));
    var request =
        new PortfolioTransactionRequest(
            2L,
            PortfolioTransactionType.BUY,
            date,
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
    verifyNoInteractions(holdingRepository, historyRepository, ledgerCalculator, snapshotService);
  }
}
