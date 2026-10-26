package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection;
import org.grit.daynomy.emailsubscription.repository.EmailAnalysisExecutionRepository;
import org.grit.daynomy.emailsubscription.repository.EmailAnalysisExecutionRepository.Job;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class EmailAnalysisSchedulerTest {
  private final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  private final ImportantStockSelectionService selection =
      mock(ImportantStockSelectionService.class);
  private final EmailStockAnalysisService analysis = mock(EmailStockAnalysisService.class);
  private final EmailAnalysisExecutionRepository executions =
      mock(EmailAnalysisExecutionRepository.class);
  private final Instant now = Instant.parse("2026-10-25T22:00:00Z");
  private final LocalDate date = LocalDate.of(2026, 10, 26);
  private final Job job = new Job(UUID.randomUUID(), 1L, now);
  private final UUID callId = UUID.randomUUID();
  private final EmailAnalysisPolicy policy =
      new EmailAnalysisPolicy(
          true,
          true,
          "model",
          BigDecimal.TEN,
          new BigDecimal("100"),
          BigDecimal.ONE,
          new BigDecimal("2"),
          BigDecimal.ONE,
          new BigDecimal("8"),
          new BigDecimal("0.01"),
          2000,
          3);
  private EmailAnalysisScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new EmailAnalysisScheduler(
            subscriptions,
            selection,
            analysis,
            executions,
            policy,
            Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void doesNotSelectOrCallAiOutsideTheExecutionWindow() {
    scheduler =
        new EmailAnalysisScheduler(
            subscriptions,
            selection,
            analysis,
            executions,
            policy,
            Clock.fixed(Instant.parse("2026-10-25T23:45:00Z"), ZoneOffset.UTC));
    scheduler.analyzeDaily();
    verifyNoInteractions(subscriptions, selection, analysis, executions);
  }

  @Test
  void deduplicatesMemberSelectionsAndReusesResultsWithoutExtraAiCalls() {
    prepare();
    var result = mock(CommonStockAnalysis.class);
    when(result.getId()).thenReturn(5L);
    when(analysis.analyzeStock(1L, now)).thenReturn(Optional.of(result));
    scheduler.analyzeDaily();
    verify(executions).register(date, now, List.of(1L));
    verify(executions).finish(job.id(), "SUCCEEDED", 5L, now);
    verify(executions, never()).reserve(any(), anyString(), anyString(), any(), any());
    verify(executions, times(1)).startAttempt(job.id());
  }

  @Test
  void recordsEachActualCallAndAppliesRequestLimits() {
    prepare();
    var request =
        request(
            "{\"usage\":{\"input_tokens\":1000,\"input_tokens_details\":{\"cached_tokens\":200},\"output_tokens\":100},\"output\":[{\"type\":\"web_search_call\"}]}");
    when(executions.reserve(job, "source_search", "model", now, policy)).thenReturn(callId);
    when(analysis.analyzeStock(1L, now))
        .thenAnswer(
            invocation -> {
              assertThat(PortfolioAnalysisCall.prepare(Map.of("model", "model")))
                  .containsEntry("max_output_tokens", 2000)
                  .containsEntry("max_tool_calls", 3);
              PortfolioAnalysisCall.execute(request, "source_search", "model");
              return Optional.empty();
            });
    scheduler.analyzeDaily();
    var cost = ArgumentCaptor.forClass(BigDecimal.class);
    verify(executions)
        .completeCall(
            eq(callId),
            eq("COMPLETED"),
            eq(1000L),
            eq(200L),
            eq(100L),
            eq(1L),
            cost.capture(),
            isNull(),
            eq(now));
    assertThat(cost.getValue()).isEqualByComparingTo("0.0126");
    assertThat(PortfolioAnalysisCall.prepare(Map.of("model", "model")))
        .doesNotContainKey("max_output_tokens");
  }

  @Test
  void retriesOnlyOneAdditionalTimeForTransientErrors() {
    prepare();
    var request = mock(RestClient.RequestHeadersSpec.class);
    when(request.retrieve()).thenThrow(new ResourceAccessException("timeout"));
    when(executions.reserve(job, "initial", "model", now, policy)).thenReturn(callId);
    when(analysis.analyzeStock(1L, now))
        .thenAnswer(
            invocation -> {
              try {
                PortfolioAnalysisCall.execute(request, "initial", "model");
              } catch (ResourceAccessException expected) {
                return Optional.empty();
              }
              return Optional.empty();
            });
    scheduler.analyzeDaily();
    verify(analysis, times(2)).analyzeStock(1L, now);
    verify(executions, times(2)).startAttempt(job.id());
    verify(executions).finish(job.id(), "FAILED", null, now);
  }

  @Test
  void unknownUsageStopsInsteadOfBeingRecordedAsZero() {
    prepare();
    var request = request("{}");
    when(executions.reserve(job, "initial", "model", now, policy)).thenReturn(callId);
    when(analysis.analyzeStock(1L, now))
        .thenAnswer(
            invocation -> {
              PortfolioAnalysisCall.execute(request, "initial", "model");
              return Optional.empty();
            });
    scheduler.analyzeDaily();
    verify(executions).completeCall(callId, "UNKNOWN", null, null, null, null, null, null, now);
    verify(analysis, times(1)).analyzeStock(1L, now);
    verify(executions).finish(job.id(), "FAILED", null, now);
  }

  @Test
  void skipsStocksNoSubscriberCurrentlyHolds() {
    prepare();
    when(executions.hasSubscriberHolding(1L)).thenReturn(false);
    scheduler.analyzeDaily();
    verifyNoInteractions(analysis);
    verify(executions).finish(job.id(), "SKIPPED", null, now);
  }

  @Test
  void doesNotPublishAnAnalysisCompletedAfterTheCutoff() {
    prepare();
    var clock = mock(Clock.class);
    var time = new java.util.concurrent.atomic.AtomicReference<>(now);
    when(clock.instant()).thenAnswer(invocation -> time.get());
    scheduler =
        new EmailAnalysisScheduler(subscriptions, selection, analysis, executions, policy, clock);
    var result = mock(CommonStockAnalysis.class);
    when(analysis.analyzeStock(1L, now))
        .thenAnswer(
            invocation -> {
              time.set(now.plusSeconds(6300));
              return Optional.of(result);
            });
    scheduler.analyzeDaily();
    verify(executions).finish(job.id(), "FAILED", null, now.plusSeconds(6300));
    verify(executions, never()).finish(any(), eq("SUCCEEDED"), any(), any());
  }

  @Test
  void rechecksTheCutoffAfterWaitingForBudgetReservation() {
    prepare();
    var clock = mock(Clock.class);
    var time = new java.util.concurrent.atomic.AtomicReference<>(now);
    when(clock.instant()).thenAnswer(invocation -> time.get());
    scheduler =
        new EmailAnalysisScheduler(subscriptions, selection, analysis, executions, policy, clock);
    when(executions.reserve(job, "initial", "model", now, policy))
        .thenAnswer(
            invocation -> {
              time.set(now.plusSeconds(6300));
              return callId;
            });
    var request = mock(RestClient.RequestHeadersSpec.class);
    when(analysis.analyzeStock(1L, now))
        .thenAnswer(
            invocation -> {
              PortfolioAnalysisCall.execute(request, "initial", "model");
              return Optional.empty();
            });
    scheduler.analyzeDaily();
    verifyNoInteractions(request);
    verify(executions)
        .completeCall(
            callId, "FAILED", 0L, 0L, 0L, 0L, BigDecimal.ZERO, null, now.plusSeconds(6300));
  }

  private void prepare() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    when(subscriptions.findEligibleMemberIds(eq(2L), any())).thenReturn(List.of());
    var selected =
        new ImportantStockSelection(
            date.minusDays(3),
            date.minusDays(4),
            true,
            List.of(
                new ImportantStockSelection.Stock(
                    1L, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)));
    when(selection.select(anyLong(), eq(date))).thenReturn(Optional.of(selected));
    when(executions.claim(date, now, policy)).thenReturn(Optional.of(job), Optional.empty());
    when(executions.hasSubscriberHolding(1L)).thenReturn(true);
  }

  private RestClient.RequestHeadersSpec<?> request(String body) {
    var request = mock(RestClient.RequestHeadersSpec.class);
    var response = mock(RestClient.ResponseSpec.class);
    when(request.retrieve()).thenReturn(response);
    when(response.body(String.class)).thenReturn(body);
    return request;
  }
}
