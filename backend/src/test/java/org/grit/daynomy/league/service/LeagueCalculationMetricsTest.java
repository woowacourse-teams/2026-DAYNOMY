package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeagueCalculationMetricsTest {
  @Test
  void countsCommittedProfilesKeepsFailureHistoryAndResetsLatestSnapshots() {
    var registry = new SimpleMeterRegistry();
    var metrics = new LeagueCalculationMetrics(registry);
    long started = metrics.startRun();
    metrics.resetProfiles(2);
    metrics.profileSucceeded(Map.of("CALCULATED", 3, "MISSING_PRICE", 1));
    metrics.profileFailed();
    metrics.finalizationState(2, 1, Instant.now().minusSeconds(10800));
    metrics.finishRun(started, true);
    assertThat(
            registry
                .get("daynomy.league.calculation.latest.profiles")
                .tag("outcome", "failed")
                .gauge()
                .value())
        .isEqualTo(1);
    assertThat(
            registry
                .get("daynomy.league.calculation.latest.days")
                .tag("reason", "MISSING_PRICE")
                .gauge()
                .value())
        .isEqualTo(1);
    assertThat(registry.get("daynomy.league.finalization.delay.seconds").gauge().value())
        .isGreaterThanOrEqualTo(10800);
    assertThat(registry.get("daynomy.league.calculation.duration").timer().count()).isEqualTo(1);
    metrics.resetProfiles(0);
    metrics.finalizationState(0, 0, null);
    assertThat(
            registry
                .get("daynomy.league.calculation.latest.profiles")
                .tag("outcome", "failed")
                .gauge()
                .value())
        .isZero();
    assertThat(registry.get("daynomy.league.finalization.delay.seconds").gauge().value()).isZero();
    assertThat(registry.get("daynomy.league.calculation.profile.failures").counter().count())
        .isEqualTo(1);
    metrics.finishRun(metrics.startRun(), false);
    assertThat(registry.get("daynomy.league.calculation.latest.run.failed").gauge().value())
        .isEqualTo(1);
    metrics.finishRun(metrics.startRun(), true);
    assertThat(registry.get("daynomy.league.calculation.latest.run.failed").gauge().value())
        .isZero();
  }
}
