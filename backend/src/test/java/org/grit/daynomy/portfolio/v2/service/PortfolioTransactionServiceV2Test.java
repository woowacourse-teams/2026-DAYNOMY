package org.grit.daynomy.portfolio.v2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
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
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PortfolioTransactionServiceV2Test {
  @Mock private MemberRepository members;
  @Mock private PortfolioRepository portfolios;
  @Mock private AssetRepository assets;
  @Mock private PortfolioHoldingRepository holdings;
  @Mock private PortfolioHoldingHistoryRepository histories;
  @Spy private PortfolioLedgerCalculator calculator = new PortfolioLedgerCalculator();
  @Mock private PortfolioSnapshotServiceV2 snapshots;
  @InjectMocks private PortfolioTransactionServiceV2 service;

  @Test
  void locksBeforeReadingAndReusesOrderedHistoryForSnapshots() {
    Portfolio portfolio = prepare();
    Asset asset = assets.findById(2L).orElseThrow();
    PortfolioHolding holding = new PortfolioHolding(portfolio, asset, 3, new BigDecimal("100"));
    LocalDate day = LocalDate.of(2026, 10, 1);
    List<PortfolioHoldingHistory> ordered =
        new ArrayList<>(
            List.of(
                new PortfolioHoldingHistory(
                    portfolio,
                    asset,
                    PortfolioHoldingChangeType.OPENING_BALANCE,
                    3,
                    new BigDecimal("100"),
                    day,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)));
    given(holdings.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));
    given(histories.saveAndFlush(any()))
        .willAnswer(
            invocation -> {
              PortfolioHoldingHistory added = invocation.getArgument(0);
              ReflectionTestUtils.setField(added, "id", 99L);
              ordered.add(added);
              return added;
            });
    given(histories.findOrderedWithAssetsByPortfolioId(10L)).willReturn(ordered);
    var response =
        service.transact(
            1L,
            new PortfolioTransactionRequest(
                2L,
                PortfolioTransactionType.SELL,
                day.plusDays(1),
                2L,
                new BigDecimal("120"),
                new BigDecimal("3"),
                new BigDecimal("4")));
    assertThat(response.transactionId()).isEqualTo(99L);
    assertThat(response.holding().quantity()).isEqualTo(1L);
    assertThat(response.totalRealizedProfitLoss()).isEqualByComparingTo("33");
    then(histories).should().findOrderedWithAssetsByPortfolioId(10L);
    then(snapshots).should().refresh(portfolio, ordered, day.plusDays(1));
    var order = inOrder(members, portfolios, histories);
    order.verify(members).findByIdForUpdate(1L);
    order.verify(portfolios).findByMemberId(1L);
    order.verify(portfolios).findByIdForUpdate(10L);
    order
        .verify(histories)
        .existsByPortfolioIdAndAssetIdAndChangeTypeInAndEffectiveDateAfter(
            eq(10L), eq(2L), anyCollection(), eq(day.plusDays(1)));
  }

  @Test
  void rejectsTradeBeforeLaterBalanceWithoutSaving() {
    prepare();
    LocalDate day = LocalDate.of(2026, 10, 1);
    given(
            histories.existsByPortfolioIdAndAssetIdAndChangeTypeInAndEffectiveDateAfter(
                eq(10L), eq(2L), anyCollection(), eq(day)))
        .willReturn(true);
    assertThatThrownBy(
            () ->
                service.transact(
                    1L,
                    new PortfolioTransactionRequest(
                        2L,
                        PortfolioTransactionType.BUY,
                        day,
                        1L,
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO)))
        .isInstanceOf(BusinessException.class);
    then(histories).should(never()).saveAndFlush(any());
    then(snapshots).shouldHaveNoInteractions();
  }

  private Portfolio prepare() {
    Member member = mock(Member.class);
    Portfolio portfolio = mock(Portfolio.class);
    Asset asset = mock(Asset.class);
    given(portfolio.getId()).willReturn(10L);
    given(asset.getId()).willReturn(2L);
    given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolios.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolios.findByIdForUpdate(10L)).willReturn(Optional.of(portfolio));
    given(assets.findById(2L)).willReturn(Optional.of(asset));
    return portfolio;
  }
}
