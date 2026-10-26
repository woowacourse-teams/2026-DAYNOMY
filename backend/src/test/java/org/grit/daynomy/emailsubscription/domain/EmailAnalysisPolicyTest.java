package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EmailAnalysisPolicyTest {
  public static EmailAnalysisPolicy policy() {
    return new EmailAnalysisPolicy(
        true,
        true,
        "model",
        new BigDecimal("10"),
        new BigDecimal("100"),
        new BigDecimal("1"),
        new BigDecimal("2"),
        new BigDecimal("1"),
        new BigDecimal("8"),
        new BigDecimal("0.01"),
        2000,
        3);
  }

  @Test
  void runsOnlyBetweenSevenAndTheExclusiveCutoff() {
    assertThat(policy().canStart(Instant.parse("2026-10-25T21:59:59Z"))).isFalse();
    assertThat(policy().canStart(Instant.parse("2026-10-25T22:00:00Z"))).isTrue();
    assertThat(policy().canStart(Instant.parse("2026-10-25T23:44:59Z"))).isTrue();
    assertThat(policy().canStart(Instant.parse("2026-10-25T23:45:00Z"))).isFalse();
  }

  @Test
  void requiresMeasuredCostsBeforeActivation() {
    assertThatThrownBy(
            () ->
                new EmailAnalysisPolicy(
                    true, false, "model", null, null, null, null, null, null, null, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new EmailAnalysisPolicy(
                    false, false, null, null, null, null, null, null, null, null, 0, 0)
                .canStart(Instant.parse("2026-10-25T22:00:00Z")))
        .isFalse();
  }

  @Test
  void accountsForCachedTokensOutputAndSearchesWithoutDoubleCountingInput() {
    assertThat(policy().cost(1_000_000, 200_000, 100_000, 3)).isEqualByComparingTo("2.63");
    assertThatThrownBy(() -> policy().cost(1, 2, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
