package org.grit.daynomy.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.junit.jupiter.api.Test;

class HoldingReasonTest {
  @Test
  void newestDecisionWinsAndLegacyReasonIsPreservedWhenNoDecisionExists() {
    var asset = mock(Asset.class);
    given(asset.getId()).willReturn(1L);
    var portfolio = mock(SharedPortfolio.class);
    var holding = new SharedHolding(portfolio, asset, 1, new BigDecimal("100"));
    holding.describe(false, "기존 이유");
    var recent = mock(InvestmentDecision.class);
    var earlier = mock(InvestmentDecision.class);
    given(recent.getAsset()).willReturn(asset);
    given(earlier.getAsset()).willReturn(asset);
    given(recent.getDecisionReason()).willReturn("최근 판단");
    given(earlier.getDecisionReason()).willReturn("최초 판단");
    var reasons = InvestmentDecision.latestReasons(List.of(recent, earlier));
    assertThat(holding.reasonFrom(reasons)).isEqualTo("최근 판단");
    assertThat(holding.reasonFrom(Map.of())).isEqualTo("기존 이유");
    assertThat(holding.getReason()).isEqualTo("기존 이유");
    assertThat(earlier.getDecisionReason()).isEqualTo("최초 판단");
  }
}
