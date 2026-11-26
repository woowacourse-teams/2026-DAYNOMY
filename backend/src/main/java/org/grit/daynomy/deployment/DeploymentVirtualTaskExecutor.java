package org.grit.daynomy.deployment;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** A timed-out HTTP request does not imply its virtual-thread analysis has finished. */
public class DeploymentVirtualTaskExecutor extends SimpleAsyncTaskExecutor {
  private final AtomicInteger pending = new AtomicInteger();

  public DeploymentVirtualTaskExecutor(String prefix) {
    super(prefix);
  }

  public int pendingTasks() {
    return pending.get();
  }

  @Override
  protected void doExecute(Runnable task) {
    pending.incrementAndGet();
    var released = new AtomicBoolean();
    Runnable release =
        () -> {
          if (released.compareAndSet(false, true)) pending.decrementAndGet();
        };
    try {
      super.doExecute(
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
}
