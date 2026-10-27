package org.grit.daynomy.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskRejectedException;

class PortfolioAnalysisTaskConfigTest {

  @Test
  void limitsConcurrencyAndPropagatesRequestContextWithoutLeakingItToTheNextTask()
      throws Exception {
    var executor = new PortfolioAnalysisTaskConfig().analysisTaskExecutor(1);
    executor.initialize();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var requestId = new AtomicReference<String>();
    try {
      MDC.put("requestId", "background-analysis-test");
      var first =
          executor.submit(
              () -> {
                requestId.set(MDC.get("requestId"));
                started.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return null;
              });
      MDC.clear();
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(requestId.get()).isEqualTo("background-analysis-test");
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(TaskRejectedException.class);
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(5))
          .until(() -> executor.getActiveCount() == 0);
      assertThat(executor.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS)).isNull();
    } finally {
      release.countDown();
      MDC.clear();
      executor.shutdown();
    }
  }
}
