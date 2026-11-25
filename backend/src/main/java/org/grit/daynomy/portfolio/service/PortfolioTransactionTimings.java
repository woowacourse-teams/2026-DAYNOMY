package org.grit.daynomy.portfolio.service;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fixed labels only; no member IDs, request keys or financial values. */
@Component
public class PortfolioTransactionTimings {
  public enum Api {
    V1,
    V2
  }

  public enum Phase {
    TRANSACTION_TOTAL,
    MEMBER_LOCK_QUERY,
    MEMBER_LOCK_HELD,
    PORTFOLIO_ACCESS,
    PORTFOLIO_LOCK_QUERY,
    HISTORY_READ,
    LEDGER_REPLAY,
    SNAPSHOT_REFRESH,
    IDEMPOTENCY_LOOKUP,
    IDEMPOTENCY_RECHECK,
    RESULT_WRITE
  }

  private final Clock clock;
  private final Map<Api, Map<Phase, Timer>> timers = new EnumMap<>(Api.class);

  public PortfolioTransactionTimings(MeterRegistry registry) {
    clock = registry.config().clock();
    for (Api api : Api.values()) {
      Map<Phase, Timer> phases = new EnumMap<>(Phase.class);
      for (Phase phase : Phase.values()) {
        phases.put(
            phase,
            Timer.builder("daynomy.portfolio.transaction.phase")
                .tag("api", api.name().toLowerCase(Locale.ROOT))
                .tag("phase", phase.name().toLowerCase(Locale.ROOT))
                .register(registry));
      }
      timers.put(api, phases);
    }
  }

  public <T> T record(Api api, Phase phase, Supplier<T> work) {
    return timers.get(api).get(phase).record(work);
  }

  public void run(Api api, Phase phase, Runnable work) {
    timers.get(api).get(phase).record(work);
  }

  /** Application-side estimate: lock query returned until commit/rollback callback. */
  public void memberLocked(Api api) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) return;
    boolean alreadyObserved =
        TransactionSynchronizationManager.getSynchronizations().stream()
            .anyMatch(s -> s instanceof LockSample sample && sample.api == api);
    if (!alreadyObserved) {
      TransactionSynchronizationManager.registerSynchronization(new LockSample(api));
    }
  }

  private final class LockSample implements TransactionSynchronization {
    private final Api api;
    private final long started = clock.monotonicTime();
    private boolean recorded;

    private LockSample(Api api) {
      this.api = api;
    }

    @Override
    public int getOrder() {
      return Integer.MIN_VALUE;
    }

    @Override
    public void afterCommit() {
      finish();
    }

    @Override
    public void afterCompletion(int status) {
      finish();
    }

    private void finish() {
      if (!recorded) {
        recorded = true;
        timers
            .get(api)
            .get(Phase.MEMBER_LOCK_HELD)
            .record(clock.monotonicTime() - started, TimeUnit.NANOSECONDS);
      }
    }
  }
}
