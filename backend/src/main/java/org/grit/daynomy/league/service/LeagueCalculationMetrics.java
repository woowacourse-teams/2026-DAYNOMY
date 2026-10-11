package org.grit.daynomy.league.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class LeagueCalculationMetrics {
  private final Map<String, AtomicLong> profiles =
      Map.of("target", new AtomicLong(), "succeeded", new AtomicLong(), "failed", new AtomicLong());
  private final Map<String, AtomicLong> days =
      Map.of(
          "CALCULATED",
          new AtomicLong(),
          "NO_HOLDINGS",
          new AtomicLong(),
          "UNSUPPORTED_ASSET",
          new AtomicLong(),
          "MISSING_PRICE",
          new AtomicLong(),
          "PRICE_COLLECTION_PENDING",
          new AtomicLong());
  private final AtomicLong lastCompleted = new AtomicLong();
  private final AtomicLong latestRunFailed = new AtomicLong();
  private final AtomicLong calendarMissingDays = new AtomicLong();
  private final AtomicLong pendingWeeks = new AtomicLong();
  private final AtomicLong oldestPendingClose = new AtomicLong();
  private final Counter profileFailures;
  private final Counter runFailures;
  private final Timer duration;

  public LeagueCalculationMetrics(MeterRegistry registry) {
    profiles.forEach(
        (outcome, count) ->
            Gauge.builder("daynomy.league.calculation.latest.profiles", count, AtomicLong::get)
                .tag("outcome", outcome)
                .register(registry));
    days.forEach(
        (reason, count) ->
            Gauge.builder("daynomy.league.calculation.latest.days", count, AtomicLong::get)
                .tag("reason", reason)
                .register(registry));
    Gauge.builder(
            "daynomy.league.calculation.last.completed.epoch.seconds",
            lastCompleted,
            AtomicLong::get)
        .register(registry);
    Gauge.builder("daynomy.league.calculation.latest.run.failed", latestRunFailed, AtomicLong::get)
        .register(registry);
    Gauge.builder("daynomy.league.calendar.missing.days", calendarMissingDays, AtomicLong::get)
        .register(registry);
    Gauge.builder("daynomy.league.finalization.pending.weeks", pendingWeeks, AtomicLong::get)
        .register(registry);
    Gauge.builder(
            "daynomy.league.finalization.delay.seconds",
            this,
            LeagueCalculationMetrics::finalizationDelay)
        .register(registry);
    profileFailures = registry.counter("daynomy.league.calculation.profile.failures");
    runFailures = registry.counter("daynomy.league.calculation.run.failures");
    duration = registry.timer("daynomy.league.calculation.duration");
  }

  public long startRun() {
    return System.nanoTime();
  }

  public void finishRun(long started, boolean completed) {
    duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    latestRunFailed.set(completed ? 0 : 1);
    if (completed) lastCompleted.set(Instant.now().getEpochSecond());
    else runFailures.increment();
  }

  public void resetProfiles(int target) {
    profiles.values().forEach(count -> count.set(0));
    days.values().forEach(count -> count.set(0));
    profiles.get("target").set(target);
  }

  public void profileSucceeded(Map<String, Integer> counts) {
    profiles.get("succeeded").incrementAndGet();
    counts.forEach(
        (reason, count) -> {
          AtomicLong value = days.get(reason);
          if (value != null) value.addAndGet(count);
        });
  }

  public void profileFailed() {
    profiles.get("failed").incrementAndGet();
    profileFailures.increment();
  }

  public void finalizationState(int missingDays, int pending, Instant oldestClose) {
    calendarMissingDays.set(missingDays);
    pendingWeeks.set(pending);
    oldestPendingClose.set(oldestClose == null ? 0 : oldestClose.getEpochSecond());
  }

  private double finalizationDelay() {
    long close = oldestPendingClose.get();
    return close == 0 ? 0 : Math.max(0, Instant.now().getEpochSecond() - close);
  }
}
