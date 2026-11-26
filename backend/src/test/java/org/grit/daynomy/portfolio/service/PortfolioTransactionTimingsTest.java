package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.portfolio.service.PortfolioTransactionTimings.Api.V2;
import static org.grit.daynomy.portfolio.service.PortfolioTransactionTimings.Phase.*;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PortfolioTransactionTimingsTest {
  private final MockClock clock = new MockClock();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
  private final PortfolioTransactionTimings timings = new PortfolioTransactionTimings(registry);

  @AfterEach
  void clearTransactionState() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
    registry.close();
  }

  @Test
  void failedPhaseIsTimedAndOriginalExceptionIsPreserved() {
    var failure = new IllegalArgumentException("invalid trade");
    assertThatThrownBy(
            () ->
                timings.record(
                    V2,
                    LEDGER_REPLAY,
                    () -> {
                      clock.add(Duration.ofMillis(3));
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(timer("ledger_replay").count()).isEqualTo(1);
    assertThat(timer("ledger_replay").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3);
  }

  @Test
  void repeatedMemberQueriesShareOneHoldSampleAndExcludeLaterCommitListeners() {
    begin();
    timings.memberLocked(V2);
    clock.add(Duration.ofMillis(2));
    timings.memberLocked(V2);
    var callbacks = TransactionSynchronizationManager.getSynchronizations();
    assertThat(callbacks).hasSize(1);
    assertThat(callbacks.getFirst().getOrder()).isEqualTo(Integer.MIN_VALUE);
    clock.add(Duration.ofMillis(3));
    callbacks.getFirst().afterCommit();
    clock.add(Duration.ofSeconds(10));
    callbacks.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    assertThat(timer("member_lock_held").count()).isEqualTo(1);
    assertThat(timer("member_lock_held").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5);
  }

  @Test
  void rollbackAlsoFinishesTheHoldSample() {
    begin();
    timings.memberLocked(V2);
    clock.add(Duration.ofMillis(7));
    TransactionSynchronizationManager.getSynchronizations()
        .getFirst()
        .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(timer("member_lock_held").count()).isEqualTo(1);
    assertThat(timer("member_lock_held").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7);
  }

  @Test
  void noHoldSampleIsInventedWithoutAnActualTransaction() {
    timings.memberLocked(V2);
    assertThat(timer("member_lock_held").count()).isZero();
  }

  private void begin() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  private Timer timer(String phase) {
    return registry
        .get("daynomy.portfolio.transaction.phase")
        .tags("api", "v2", "phase", phase)
        .timer();
  }
}
