package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class LeagueAutoSyncServiceTest {
  @Mock private MemberRepository members;
  @Mock private PortfolioRepository originals;
  @Mock private PortfolioHoldingRepository originalHoldings;
  @Mock private SharedPortfolioRepository sharedPortfolios;
  @Mock private SharedHoldingRepository sharedHoldings;
  @Mock private SharedHoldingHistoryRepository histories;
  @Mock private InvestmentDecisionRepository decisions;
  @Mock private InvestorProfileRepository profiles;
  @Mock private LeagueRankingCache rankingCache;
  @Mock private PlatformTransactionManager transactionManager;
  @InjectMocks private LeagueAutoSyncService service;

  @Test
  void missingMemberDoesNotCreateLeagueParticipation() {
    given(members.findByIdForUpdate(1L)).willReturn(Optional.empty());

    service.reconcile(1L);

    then(originals).shouldHaveNoInteractions();
  }

  @Test
  void newHoldingWithoutReasonStaysPrivateAndEntersReturnHistory() {
    Member member = mock(Member.class);
    given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    var shared = SharedPortfolio.create(member);
    ReflectionTestUtils.setField(shared, "id", 2L);
    given(sharedPortfolios.findByMemberId(1L)).willReturn(Optional.of(shared));
    var original = Portfolio.create(member);
    ReflectionTestUtils.setField(original, "id", 3L);
    given(originals.findByMemberId(1L)).willReturn(Optional.of(original));
    Asset asset = asset();
    given(originalHoldings.findAllByPortfolioIdOrderById(3L))
        .willReturn(List.of(new PortfolioHolding(original, asset, 2, new BigDecimal("100.00"))));
    given(sharedHoldings.findAllByPortfolioIdOrderById(2L)).willReturn(List.of());

    service.reconcile(1L);

    var saved = ArgumentCaptor.forClass(SharedHolding.class);
    then(sharedHoldings).should().save(saved.capture());
    assertThat(saved.getValue().isHidden()).isTrue();
    assertThat(saved.getValue().isPendingReason()).isTrue();
    then(histories).should().save(any(SharedHoldingHistory.class));
    then(rankingCache).should().invalidateAfterCommit();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void quantityChangeKeepsVisibilityAndRecordsOnlyFutureWeights(boolean hidden) {
    Member member = mock(Member.class);
    given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    var shared = SharedPortfolio.create(member);
    ReflectionTestUtils.setField(shared, "id", 2L);
    given(sharedPortfolios.findByMemberId(1L)).willReturn(Optional.of(shared));
    var original = Portfolio.create(member);
    ReflectionTestUtils.setField(original, "id", 3L);
    given(originals.findByMemberId(1L)).willReturn(Optional.of(original));
    Asset asset = asset();
    given(originalHoldings.findAllByPortfolioIdOrderById(3L))
        .willReturn(List.of(new PortfolioHolding(original, asset, 2, new BigDecimal("100.00"))));
    var current = new SharedHolding(shared, asset, 1, new BigDecimal("100.00"));
    current.changeVisibility(hidden);
    given(sharedHoldings.findAllByPortfolioIdOrderById(2L)).willReturn(List.of(current));

    service.reconcile(1L);

    var saved = ArgumentCaptor.forClass(SharedHoldingHistory.class);
    then(histories).should().save(saved.capture());
    assertThat(saved.getValue().getChangeType()).isEqualTo(SharedHoldingHistory.ChangeType.UPDATED);
    assertThat(saved.getValue().getQuantity()).isEqualTo(2);
    assertThat(current.isHidden()).isEqualTo(hidden);
  }

  private Asset asset() {
    Asset asset =
        Asset.listedStock(
            "테스트", "005930", StockMarket.KOSPI, "KR7005930003", LocalDate.of(2026, 9, 1));
    ReflectionTestUtils.setField(asset, "id", 4L);
    return asset;
  }
}
