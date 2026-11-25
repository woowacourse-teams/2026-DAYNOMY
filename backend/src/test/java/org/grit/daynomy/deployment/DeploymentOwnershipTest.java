package org.grit.daynomy.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class DeploymentOwnershipTest {
  @TempDir Path directory;

  @Test
  void onlyCurrentOwnerRunsAndOwnershipCanChange() throws Exception {
    var calls = new AtomicInteger();
    Files.writeString(directory.resolve("active-slot"), "blue\n");
    DeploymentOwnership.run("green", directory, calls::incrementAndGet);
    DeploymentOwnership.run("blue", directory, calls::incrementAndGet);
    Files.writeString(directory.resolve("active-slot"), "green\n");
    DeploymentOwnership.run("blue", directory, calls::incrementAndGet);
    DeploymentOwnership.run("green", directory, calls::incrementAndGet);
    assertThat(calls).hasValue(2);
  }

  @Test
  void missingOwnershipFailsClosed() {
    var calls = new AtomicInteger();
    assertThatThrownBy(() -> DeploymentOwnership.run("blue", directory, calls::incrementAndGet))
        .isInstanceOf(UncheckedIOException.class);
    assertThat(calls).hasValue(0);
  }

  @Test
  void waitingJobReadsOwnershipAfterDeploymentReleasesLock() throws Exception {
    Files.writeString(directory.resolve("active-slot"), "green");
    try (var file =
            FileChannel.open(
                directory.resolve("jobs.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = file.lock()) {
      // A separate JVM is required: POSIX locks are per process.
      var builder =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  DeploymentOwnership.class
                          .getProtectionDomain()
                          .getCodeSource()
                          .getLocation()
                          .getPath()
                      + java.io.File.pathSeparator
                      + GateChild.class
                          .getProtectionDomain()
                          .getCodeSource()
                          .getLocation()
                          .getPath(),
                  GateChild.class.getName(),
                  directory.toString())
              .redirectError(ProcessBuilder.Redirect.INHERIT);
      // JVM startup diagnostics belong on stderr, not the READY protocol stream.
      // Retain CI GC options and also reproduce the startup notice in local runs.
      builder
          .environment()
          .merge(
              "JAVA_TOOL_OPTIONS",
              "-Ddaynomy.deployment.protocol=true",
              (old, added) -> old + " " + added);
      var process = builder.start();
      try {
        assertThat(process.inputReader().readLine()).isEqualTo("READY");
        assertThat(process.waitFor(150, TimeUnit.MILLISECONDS)).isFalse();
        Files.writeString(directory.resolve("active-slot"), "blue");
        lock.release();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(directory.resolve("ran")).doesNotExist();
      } finally {
        process.destroyForcibly();
      }
    }
  }

  @Test
  void inactiveCandidateDoesNotWaitForAnotherLocalTask() throws Exception {
    Files.writeString(directory.resolve("active-slot"), "blue");
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var active =
          executor.submit(
              () ->
                  DeploymentOwnership.run(
                      "blue",
                      directory,
                      () -> {
                        entered.countDown();
                        try {
                          release.await();
                        } catch (InterruptedException interrupted) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var candidate =
            executor.submit(
                () -> DeploymentOwnership.run("green", directory, calls::incrementAndGet));
        candidate.get(2, TimeUnit.SECONDS);
        assertThat(calls).hasValue(0);
      } finally {
        release.countDown();
        active.get(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void inactiveCandidateDoesNotWaitForAnotherProcessFileLock() throws Exception {
    Files.writeString(directory.resolve("active-slot"), "blue");
    try (var file =
            FileChannel.open(
                directory.resolve("jobs.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = file.lock()) {
      var process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  DeploymentOwnership.class
                          .getProtectionDomain()
                          .getCodeSource()
                          .getLocation()
                          .getPath()
                      + java.io.File.pathSeparator
                      + GateChild.class
                          .getProtectionDomain()
                          .getCodeSource()
                          .getLocation()
                          .getPath(),
                  GateChild.class.getName(),
                  directory.toString())
              .redirectError(ProcessBuilder.Redirect.INHERIT)
              .start();
      try {
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(directory.resolve("ran")).doesNotExist();
        assertThat(lock.isValid()).isTrue();
      } finally {
        process.destroyForcibly();
      }
    }
  }

  public static class GateChild {
    public static void main(String[] arguments) {
      System.out.println("READY");
      Path path = Path.of(arguments[0]);
      DeploymentOwnership.run(
          "green",
          path,
          () -> {
            try {
              Files.writeString(path.resolve("ran"), "green");
            } catch (java.io.IOException failure) {
              throw new UncheckedIOException(failure);
            }
          });
    }
  }

  @Test
  @ResourceLock("java.lang.System.properties")
  void actualScheduledRegistrationGuardsDefaultAndNamedSchedulersAndPreservesPlainTimers()
      throws Exception {
    String oldSlot = System.getProperty("daynomy.deployment.slot");
    String oldDirectory = System.getProperty("daynomy.deployment.directory");
    System.setProperty("daynomy.deployment.slot", "green");
    System.setProperty("daynomy.deployment.directory", directory.toString());
    Files.writeString(directory.resolve("active-slot"), "blue");
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(DeploymentSchedulingConfig.class, SchedulingTestConfig.class);
      context.refresh();
      assertThat(
              context.getBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
          .isInstanceOf(DeploymentSchedulingConfig.OwnershipScheduledProcessor.class);
      var ran = new CountDownLatch(1);
      context
          .getBean("taskScheduler", TaskScheduler.class)
          .schedule(ran::countDown, java.time.Instant.now());
      assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
      var job = context.getBean(Job.class);
      var named = context.getBean(NamedJob.class);
      assertThat(job.ran.await(100, TimeUnit.MILLISECONDS)).isFalse();
      assertThat(named.ran.getCount()).isEqualTo(1);
      Files.writeString(directory.resolve("active-slot"), "green");
      assertThat(job.ran.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(named.ran.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(named.thread).startsWith("named-check-");
    } finally {
      if (oldSlot == null) System.clearProperty("daynomy.deployment.slot");
      else System.setProperty("daynomy.deployment.slot", oldSlot);
      if (oldDirectory == null) System.clearProperty("daynomy.deployment.directory");
      else System.setProperty("daynomy.deployment.directory", oldDirectory);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableScheduling
  static class SchedulingTestConfig {
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
      return new ThreadPoolTaskScheduler();
    }

    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler namedScheduler() {
      var scheduler = new ThreadPoolTaskScheduler();
      scheduler.setThreadNamePrefix("named-check-");
      return scheduler;
    }

    @Bean
    Job job() {
      return new Job();
    }

    @Bean
    NamedJob namedJob() {
      return new NamedJob();
    }
  }

  public static class NamedJob {
    final CountDownLatch ran = new CountDownLatch(1);
    volatile String thread;

    @Scheduled(fixedDelay = 20, scheduler = "namedScheduler")
    public void tick() {
      thread = Thread.currentThread().getName();
      ran.countDown();
    }
  }

  public static class Job {
    final CountDownLatch ran = new CountDownLatch(1);

    @Scheduled(fixedDelay = 20)
    public void tick() {
      ran.countDown();
    }
  }
}
