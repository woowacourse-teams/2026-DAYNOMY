package org.grit.daynomy.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/** Single-host scheduler ownership; the deployment controller uses the same POSIX lock. */
public final class DeploymentOwnership {
  private static final ReentrantLock LOCAL_LOCK = new ReentrantLock(true);

  private DeploymentOwnership() {}

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
      LOCAL_LOCK.lockInterruptibly();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return;
    }
    try (var channel =
            FileChannel.open(
                directory.resolve("jobs.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var ignored = channel.lock()) {
      if (Files.readString(directory.resolve("active-slot")).trim().equals(slot)) task.run();
    } catch (IOException failure) {
      // Never run a job when ownership cannot be determined.
      throw new UncheckedIOException("Deployment scheduler ownership unavailable", failure);
    } finally {
      LOCAL_LOCK.unlock();
    }
  }
}
