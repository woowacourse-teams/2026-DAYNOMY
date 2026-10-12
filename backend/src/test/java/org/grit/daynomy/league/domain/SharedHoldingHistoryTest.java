package org.grit.daynomy.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.junit.jupiter.api.Test;

class SharedHoldingHistoryTest {
  @Test
  void reconstructsLatestPositionsIncludingRemovalAndReaddition() {
    Asset first = mock(Asset.class);
    Asset second = mock(Asset.class);
    given(first.getId()).willReturn(1L);
    given(second.getId()).willReturn(2L);
    SharedPortfolio portfolio = mock(SharedPortfolio.class);
    SharedHolding holding = new SharedHolding(portfolio, first, 10, BigDecimal.TEN);
    SharedHoldingHistory added = new SharedHoldingHistory(holding, ChangeType.ADDED);
    SharedHoldingHistory other =
        new SharedHoldingHistory(
            new SharedHolding(portfolio, second, 5, BigDecimal.TEN), ChangeType.ADDED);
    holding.change(20, BigDecimal.TEN);
    holding.changeVisibility(true);
    SharedHoldingHistory updated = new SharedHoldingHistory(holding, ChangeType.UPDATED);
    SharedHoldingHistory removed = new SharedHoldingHistory(holding, ChangeType.REMOVED);
    holding.change(3, BigDecimal.TEN);
    SharedHoldingHistory readded = new SharedHoldingHistory(holding, ChangeType.ADDED);

    assertThat(SharedHoldingHistory.reconstructHoldings(List.of())).isEmpty();
    assertThat(SharedHoldingHistory.reconstructHoldings(List.of(added, other, updated)))
        .extracting(SharedHoldingHistory::getQuantity)
        .containsExactly(20L, 5L);
    assertThat(SharedHoldingHistory.reconstructHoldings(List.of(added, other, updated, removed)))
        .containsExactly(other);
    assertThat(
            SharedHoldingHistory.reconstructHoldings(
                List.of(added, other, updated, removed, readded)))
        .containsExactly(other, readded);
  }
}
