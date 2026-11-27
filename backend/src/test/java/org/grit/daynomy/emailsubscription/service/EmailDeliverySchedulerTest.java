package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.grit.daynomy.emailsubscription.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

class EmailDeliverySchedulerTest {
  final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  final EmailDeliveryService deliveries = mock(EmailDeliveryService.class);
  final EmailDeliveryRepository jobs = mock(EmailDeliveryRepository.class);
  final EmailSendingService sending = mock(EmailSendingService.class);

  @Test
  void preparesEachSubscriberWithoutRequiringAnyAnalysisResult() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    scheduler(NOW).prepareDaily();
    verify(deliveries).prepare(1L, NOW);
    verify(deliveries).prepare(2L, NOW);
    verifyNoInteractions(sending);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-25T23:59:59Z", "2026-10-26T01:00:00Z"})
  void doesNothingOutsideSendingWindow(String at) {
    var scheduler = scheduler(Instant.parse(at));
    scheduler.prepareDaily();
    scheduler.sendDue();
    verifyNoInteractions(subscriptions, deliveries, jobs, sending);
  }

  @Test
  void sendsOneDueJobAndContinuesAfterAnotherMembersPreparationFails() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    when(deliveries.prepare(1L, NOW)).thenThrow(new IllegalStateException());
    scheduler(NOW).prepareDaily();
    verify(deliveries).prepare(2L, NOW);
    var id = UUID.randomUUID();
    when(jobs.findDueIds(eq(MAIL_DATE), eq(NOW), anyList(), any())).thenReturn(List.of(id));
    scheduler(NOW).sendDue();
    verify(sending).send(id);
  }

  @Test
  void emailSchedulerDoesNotReplaceTheDefaultScheduler() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(Scheduling.class, EmailDeliveryScheduler.class)
        .withBean(EmailSubscriptionRepository.class, () -> subscriptions)
        .withBean(EmailDeliveryService.class, () -> deliveries)
        .withBean(EmailDeliveryRepository.class, () -> jobs)
        .withBean(EmailSendingService.class, () -> sending)
        .withPropertyValues("email.delivery.enabled=true", "email.delivery.scheduling-enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(TaskScheduler.class))
                  .isSameAs(context.getBean("taskScheduler"))
                  .isNotSameAs(context.getBean("emailDeliveryTaskScheduler"));
            });
  }

  @Test
  void sendsDueMailWhilePreparationIsStillBlocked() throws Exception {
    var preparing = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var sent = new CountDownLatch(1);
    var scheduler = scheduler(NOW);
    var executor = scheduler.emailDeliveryTaskScheduler();
    var id = UUID.randomUUID();
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L));
    when(deliveries.prepare(1L, NOW))
        .thenAnswer(
            invocation -> {
              preparing.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("preparation timed out");
              return Optional.empty();
            });
    when(jobs.findDueIds(eq(MAIL_DATE), eq(NOW), anyList(), any())).thenReturn(List.of(id));
    doAnswer(
            invocation -> {
              sent.countDown();
              return null;
            })
        .when(sending)
        .send(id);
    executor.initialize();
    try {
      executor.execute(scheduler::prepareDaily);
      assertThat(preparing.await(5, TimeUnit.SECONDS)).isTrue();
      executor.execute(scheduler::sendDue);
      assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  static class Scheduling {}

  @Test
  void closesContextWithoutWaitingForFutureEmailReservations() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(Scheduling.class, EmailDeliveryScheduler.class)
        .withBean(EmailSubscriptionRepository.class, () -> subscriptions)
        .withBean(EmailDeliveryService.class, () -> deliveries)
        .withBean(EmailDeliveryRepository.class, () -> jobs)
        .withBean(EmailSendingService.class, () -> sending)
        .withPropertyValues("email.delivery.enabled=true", "email.delivery.scheduling-enabled=true")
        .run(
            context -> {
              var executor =
                  context.getBean(
                      "emailDeliveryTaskScheduler",
                      org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class);
              var future =
                  executor.schedule(
                      () -> sending.send(UUID.randomUUID()), Instant.now().plusSeconds(86400));
              try {
                assertTimeoutPreemptively(Duration.ofSeconds(5), context::close);
                assertThat(executor.getScheduledThreadPoolExecutor().isTerminated()).isTrue();
                assertThat(future.isCancelled()).isTrue();
                verifyNoInteractions(sending);
              } finally {
                executor.getScheduledThreadPoolExecutor().shutdownNow();
              }
            });
  }

  @Test
  void shutdownWaitsForInFlightEmailAndItsResultWithoutInterruptingIt() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var completed = new AtomicBoolean();
    var interrupted = new AtomicBoolean();
    var scheduler = scheduler(NOW);
    var executor = scheduler.emailDeliveryTaskScheduler();
    var id = UUID.randomUUID();
    when(jobs.findDueIds(eq(MAIL_DATE), eq(NOW), anyList(), any())).thenReturn(List.of(id));
    doAnswer(
            invocation -> {
              started.countDown();
              try {
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("test did not release email sending");
                completed.set(true);
              } catch (InterruptedException failure) {
                interrupted.set(true);
                throw failure;
              }
              return null;
            })
        .when(sending)
        .send(id);
    var shutdown =
        new FutureTask<Void>(
            () -> {
              executor.shutdown();
              return null;
            });
    var closer = new Thread(shutdown);
    executor.initialize();
    var future = executor.schedule(scheduler::prepareDaily, Instant.now().plusSeconds(86400));
    try {
      executor.execute(scheduler::sendDue);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      closer.start();
      await()
          .atMost(Duration.ofSeconds(5))
          .until(executor.getScheduledThreadPoolExecutor()::isShutdown);
      assertThat(shutdown.isDone()).isFalse();
      assertThat(interrupted).isFalse();
      assertThat(completed).isFalse();
      release.countDown();
      shutdown.get(5, TimeUnit.SECONDS);
      assertThat(completed).isTrue();
      assertThat(interrupted).isFalse();
      assertThat(future.isCancelled()).isTrue();
      assertThat(executor.getScheduledThreadPoolExecutor().isTerminated()).isTrue();
      verify(sending, times(1)).send(id);
      verifyNoInteractions(deliveries, subscriptions);
    } finally {
      release.countDown();
      executor.getScheduledThreadPoolExecutor().shutdownNow();
      closer.join(5000);
    }
  }

  private EmailDeliveryScheduler scheduler(Instant at) {
    return new EmailDeliveryScheduler(
        subscriptions, deliveries, jobs, sending, Clock.fixed(at, ZoneOffset.UTC));
  }
}
