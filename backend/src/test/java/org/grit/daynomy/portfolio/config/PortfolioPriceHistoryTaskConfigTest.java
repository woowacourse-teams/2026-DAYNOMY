package org.grit.daynomy.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class PortfolioPriceHistoryTaskConfigTest {
  @Test
  void rejectsExcessWorkWithoutRunningCollectionOnTheRequestThread() throws Exception {
    var executor = new PortfolioPriceHistoryTaskConfig().portfolioPriceHistoryTaskExecutor();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    executor.initialize();
    try {
      executor.execute(
          () -> {
            started.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          });
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      for (int index = 0; index < 32; index++) executor.execute(() -> {});
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(TaskRejectedException.class);
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }
}
