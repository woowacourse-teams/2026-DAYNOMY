package org.grit.daynomy.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class DeploymentTasksEndpointTest {

  @Test
  void rejectedSubmissionDoesNotLeaveAPhantomPendingTask() throws Exception {
    var executor = new DeploymentTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.initialize();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try {
      var future =
          executor.submit(
              () -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
              });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> executor.submit(() -> {})).isInstanceOf(TaskRejectedException.class);
      assertThat(executor.pendingTasks()).isEqualTo(1);
      release.countDown();
      future.get(5, TimeUnit.SECONDS);
      Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> executor.pendingTasks() == 0);
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }

  @Test
  void cancelledHttpFutureDoesNotHideItsStillRunningVirtualThread() throws Exception {
    try (var executor = new DeploymentVirtualTaskExecutor("drain-test-")) {
      executor.setVirtualThreads(true);
      var started = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      try {
        var future =
            executor.submit(
                () -> {
                  started.countDown();
                  release.await(5, TimeUnit.SECONDS);
                  return null;
                });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        future.cancel(false);
        assertThat(future.isDone()).isTrue();
        var endpoint =
            new DeploymentTasksEndpoint(Map.of(), Map.of("portfolioAnalysisExecutorV2", executor));
        assertThat(endpoint.status().executors().get("portfolioAnalysisExecutorV2").pending())
            .isEqualTo(1);
        release.countDown();
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> executor.pendingTasks() == 0);
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void observesRunningAndQueuedWorkAfterTheHttpRequestHasReturned() throws Exception {
    var executor = new DeploymentTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.initialize();
    var endpoint =
        new DeploymentTasksEndpoint(Map.of("portfolioAnalysisTaskExecutor", executor), Map.of());
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try {
      executor.execute(
          () -> {
            started.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      executor.execute(() -> {});
      assertThat(endpoint.status().executors().get("portfolioAnalysisTaskExecutor"))
          .isEqualTo(new DeploymentTasksEndpoint.Tasks(1, 1, 2));
      release.countDown();
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThat(endpoint.status().executors().get("portfolioAnalysisTaskExecutor"))
                      .isEqualTo(new DeploymentTasksEndpoint.Tasks(0, 0, 0)));
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }
}
