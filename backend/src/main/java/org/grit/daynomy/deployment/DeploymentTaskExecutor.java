package org.grit.daynomy.deployment;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Count work before submission, including the gap between dequeue and worker execution. */
public class DeploymentTaskExecutor extends ThreadPoolTaskExecutor {
  private final AtomicInteger pending = new AtomicInteger();

  public int pendingTasks() {
    return pending.get();
  }

  @Override
  public void execute(Runnable task) {
    pending.incrementAndGet();
    var released = new AtomicBoolean();
    Runnable release =
        () -> {
          if (released.compareAndSet(false, true)) pending.decrementAndGet();
        };
    try {
      super.execute(
          () -> {
            try {
              task.run();
            } finally {
              release.run();
            }
          });
    } catch (RuntimeException | Error failure) {
      release.run();
      throw failure;
    }
  }

  @Override
  public Future<?> submit(Runnable task) {
    var future = new FutureTask<Void>(task, null);
    execute(future);
    return future;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    var future = new FutureTask<T>(task);
    execute(future);
    return future;
  }
}
