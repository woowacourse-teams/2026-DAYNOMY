package org.grit.daynomy.deployment;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** HTTP 응답 이후에도 실행되는 작업을 이전 JVM 종료 전에 확인한다. */
@Component
@Endpoint(id = "deploymenttasks")
@Conditional(DeploymentSchedulingConfig.SlotEnabledCondition.class)
public class DeploymentTasksEndpoint {

  private final Map<String, ThreadPoolTaskExecutor> executors;
  private final Map<String, SimpleAsyncTaskExecutor> virtualExecutors;

  public DeploymentTasksEndpoint(
      Map<String, ThreadPoolTaskExecutor> executors,
      Map<String, SimpleAsyncTaskExecutor> virtualExecutors) {
    this.executors = Map.copyOf(executors);
    this.virtualExecutors = Map.copyOf(virtualExecutors);
  }

  @ReadOperation
  public Status status() {
    var tasks = new TreeMap<String, Tasks>();
    executors.forEach(
        (name, executor) ->
            tasks.put(
                name,
                new Tasks(
                    executor.getActiveCount(),
                    executor.getThreadPoolExecutor().getQueue().size(),
                    executor instanceof DeploymentTaskExecutor tracked
                        ? tracked.pendingTasks()
                        : -1)));
    virtualExecutors.forEach(
        (name, executor) -> {
          int pending =
              executor instanceof DeploymentVirtualTaskExecutor tracked
                  ? tracked.pendingTasks()
                  : -1;
          tasks.put(name, new Tasks(pending, 0, pending));
        });
    return new Status(
        System.getProperty("daynomy.deployment.slot"),
        tasks,
        new ScheduledTasks(1, DeploymentOwnership.activeTasks()));
  }

  public record Tasks(int active, int queued, int pending) {}

  public record ScheduledTasks(int protocol, int active) {}

  public record Status(String slot, Map<String, Tasks> executors, ScheduledTasks scheduled) {}
}
