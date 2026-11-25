package org.grit.daynomy.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Single-host scheduler ownership; the deployment controller uses the same POSIX lock. */
public final class DeploymentOwnership {
  private static final ReentrantLock LOCAL_LOCK = new ReentrantLock(true);
  private static final AtomicInteger ACTIVE = new AtomicInteger();

  private DeploymentOwnership() {}

  public static int activeTasks() {
    return ACTIVE.get();
  }

  public static Runnable wrap(Runnable task) {
    return () -> {
      String slot = System.getProperty("daynomy.deployment.slot");
      if (slot == null) {
        task.run();
        return;
      }
      String directory = System.getProperty("daynomy.deployment.directory");
      if (directory == null) throw new IllegalStateException("Deployment directory is required");
      run(slot, Path.of(directory), task);
    };
  }

  static void run(String slot, Path directory, Runnable task) {
    if (!slot.equals("blue") && !slot.equals("green")) {
      throw new IllegalArgumentException("Unknown deployment slot");
    }
    if (LOCAL_LOCK.isHeldByCurrentThread()) {
      task.run();
      return;
    }
    try {
      // A candidate must become ready without waiting for the active slot's batch.
      // Ownership is still checked again under the file lock before running work.
      if (!Files.readString(directory.resolve("active-slot")).trim().equals(slot)) return;
      LOCAL_LOCK.lockInterruptibly();
    } catch (IOException failure) {
      throw new UncheckedIOException("Deployment scheduler ownership unavailable", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return;
    }
    try (var channel =
        FileChannel.open(
            directory.resolve("jobs.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      // A former owner waiting behind another batch must be able to retire promptly.
      while (Files.readString(directory.resolve("active-slot")).trim().equals(slot)) {
        try (var lock = channel.tryLock()) {
          if (lock != null) {
            // Register before reading ownership: retirement cannot miss eligible work
            // between its last ownership check and the beginning of the task body.
            ACTIVE.incrementAndGet();
            try {
              if (Files.readString(directory.resolve("active-slot")).trim().equals(slot))
                task.run();
            } finally {
              ACTIVE.decrementAndGet();
            }
            return;
          }
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    } catch (IOException failure) {
      // Never run a job when ownership cannot be determined.
      throw new UncheckedIOException("Deployment scheduler ownership unavailable", failure);
    } finally {
      LOCAL_LOCK.unlock();
    }
  }
}
