package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LeagueRankingCacheTest {
  private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-24T00:00:00Z"));
  private final Clock clock = mock(Clock.class);

  private LeagueRankingCache cache() {
    when(clock.instant()).thenAnswer(call -> now.get());
    return new LeagueRankingCache(scheduler, clock);
  }

  @Test
  void concurrentColdRequestsCalculateOnlyOnce() throws Exception {
    var cache = cache();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    Supplier<LeagueService.RankingSnapshot> loader =
        () -> {
          calls.incrementAndGet();
          entered.countDown();
          try {
            if (!release.await(5, TimeUnit.SECONDS))
              throw new IllegalStateException("load timeout");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          return snapshot(List.of());
        };
    try (var executor = Executors.newFixedThreadPool(8)) {
      var futures =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(
                  index -> executor.submit(() -> cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader)))
              .toList();
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS).profiles()).isEmpty();
    }
    assertThat(calls).hasValue(1);
  }

  @Test
  void expiredEntriesRefreshOnceInBackgroundWhileReadersUseThePreviousResult() {
    var cache = cache();
    var old = snapshot(List.of(mock(LeagueService.RankedProfile.class)));
    var updated = snapshot(List.of(mock(LeagueService.RankedProfile.class)));
    var calls = new AtomicInteger();
    Supplier<LeagueService.RankingSnapshot> loader =
        () -> calls.incrementAndGet() == 1 ? old : updated;
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader)).isEqualTo(old);
    now.updateAndGet(time -> time.plusSeconds(5));
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader)).isEqualTo(old);
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader)).isEqualTo(old);
    var refresh = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(refresh.capture(), any(Instant.class));
    assertThat(calls).hasValue(1);
    refresh.getValue().run();
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader)).isEqualTo(updated);
    assertThat(calls).hasValue(2);
  }

  @Test
  void anOldBackgroundRefreshCannotRestoreAnInvalidatedEntry() {
    var cache = cache();
    var updated = snapshot(List.of(mock(LeagueService.RankedProfile.class)));
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> snapshot(List.of()));
    now.updateAndGet(time -> time.plusSeconds(5));
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> snapshot(List.of()));
    var refresh = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(refresh.capture(), any(Instant.class));
    cache.invalidateAfterCommit();
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> updated);
    refresh.getValue().run();
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> snapshot(List.of())))
        .isEqualTo(updated);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void writesInvalidateOnlyAfterCommitAndRollbackKeepsThePreviousResult(boolean committed) {
    var cache = cache();
    var updated = snapshot(List.of(mock(LeagueService.RankedProfile.class)));
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> snapshot(List.of()));
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.invalidateAfterCommit();
      assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> updated).profiles()).isEmpty();
      if (committed)
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, () -> updated))
        .isEqualTo(committed ? updated : snapshot(List.of()));
  }

  @Test
  void refreshFailurePreservesResultsAndBacksOffBeforeRetrying() {
    var cache = cache();
    var calls = new AtomicInteger();
    Supplier<LeagueService.RankingSnapshot> loader =
        () -> {
          if (calls.incrementAndGet() > 1) throw new IllegalStateException("refresh failure");
          return snapshot(List.of());
        };
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader);
    now.updateAndGet(time -> time.plusSeconds(5));
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader);
    var refresh = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(refresh.capture(), any(Instant.class));
    refresh.getValue().run();
    assertThat(cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader).profiles()).isEmpty();
    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    now.updateAndGet(time -> time.plusSeconds(5));
    cache.get(WEEK, LeagueType.WEEKLY_RETURN, loader);
    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
  }

  private LeagueService.RankingSnapshot snapshot(List<LeagueService.RankedProfile> profiles) {
    return new LeagueService.RankingSnapshot(profiles, profiles);
  }
}
