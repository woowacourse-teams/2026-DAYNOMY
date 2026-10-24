package org.grit.daynomy.league.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class LeagueRankingCache {
  private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(5);
  private final TaskScheduler taskScheduler;
  private final Clock clock;
  private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

  @Autowired
  public LeagueRankingCache(TaskScheduler taskScheduler) {
    this(taskScheduler, Clock.systemUTC());
  }

  LeagueRankingCache(TaskScheduler taskScheduler, Clock clock) {
    this.taskScheduler = taskScheduler;
    this.clock = clock;
  }

  List<LeagueService.RankedProfile> get(
      LocalDate week, LeagueType type, Supplier<List<LeagueService.RankedProfile>> loader) {
    entries.keySet().removeIf(key -> key.week().isBefore(week.minusWeeks(2)));
    Entry entry = entries.computeIfAbsent(new Key(week, type), ignored -> new Entry());
    if (entry.value == null) {
      synchronized (entry) {
        if (entry.value == null) entry.load(loader);
      }
    }
    if (!clock.instant().isBefore(entry.refreshAt) && entry.refreshing.compareAndSet(false, true)) {
      try {
        taskScheduler.schedule(
            () -> {
              try {
                entry.load(loader);
              } catch (RuntimeException exception) {
                entry.refreshAt = clock.instant().plus(REFRESH_INTERVAL);
                log.warn("League ranking refresh failed: {}", exception.getClass().getSimpleName());
              } finally {
                entry.refreshing.set(false);
              }
            },
            clock.instant());
      } catch (RuntimeException exception) {
        entry.refreshing.set(false);
        throw exception;
      }
    }
    return entry.value;
  }

  public void invalidateAfterCommit() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              entries.clear();
            }
          });
    } else entries.clear();
  }

  private record Key(LocalDate week, LeagueType type) {}

  private class Entry {
    private volatile List<LeagueService.RankedProfile> value;
    private volatile Instant refreshAt;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    private void load(Supplier<List<LeagueService.RankedProfile>> loader) {
      List<LeagueService.RankedProfile> loaded = List.copyOf(loader.get());
      refreshAt = clock.instant().plus(REFRESH_INTERVAL);
      value = loaded;
    }
  }
}
