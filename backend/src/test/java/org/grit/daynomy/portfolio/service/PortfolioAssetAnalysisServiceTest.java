package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.AssetImpactResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.PortfolioAssetAnalysis;
import org.grit.daynomy.portfolio.repository.PortfolioAssetAnalysisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PortfolioAssetAnalysisServiceTest {
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(PortfolioAssetAnalysisService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  @Mock private PortfolioAssetAnalysisRepository repository;
  @Mock private PortfolioAnalysisAiClient ai;
  private PortfolioAssetAnalysisService service;
  private final List<PortfolioAnalysisTarget> targets =
      List.of(new PortfolioAnalysisTarget("삼성전자"), new PortfolioAnalysisTarget("SK하이닉스"));

  @BeforeEach
  void setUp() {
    appender.start();
    logger.addAppender(appender);
    service =
        new PortfolioAssetAnalysisService(repository, ai, "test-model", Duration.ofMinutes(30));
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void analyzesAllMissingAssetsInOneRequestAndStoresOnlyCommonData() throws Exception {
    given(ai.analyze(targets))
        .willReturn(result(impact("삼성전자", ImpactLevel.LOW), impact("SK하이닉스", ImpactLevel.HIGH)));

    var response = service.analyze(targets);

    verify(ai).analyze(targets);
    assertSummary(0, 2, 0, "SUCCESS");
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(response.impacts()).extracting(AssetImpactResult::sortOrder).containsExactly(1, 2);
    var snapshot = ArgumentCaptor.forClass(String.class);
    var analyzedAt = ArgumentCaptor.forClass(Instant.class);
    var expiresAt = ArgumentCaptor.forClass(Instant.class);
    verify(repository, times(2))
        .upsert(
            anyString(),
            eq("asset-v4-grounded-source"),
            eq("test-model"),
            snapshot.capture(),
            analyzedAt.capture(),
            expiresAt.capture());
    for (int i = 0; i < 2; i++) {
      var json = new ObjectMapper().readTree(snapshot.getAllValues().get(i));
      assertThat(json.toString()).doesNotContain("weight", "quantity", "memberId", "overallImpact");
      assertThat(json.path("sortOrder").asInt()).isZero();
      assertThat(json.path("sources").get(0).path("factQuote").asText())
          .isEqualTo("Revenue increased.");
      assertThat(
              Duration.between(analyzedAt.getAllValues().get(i), expiresAt.getAllValues().get(i)))
          .isEqualTo(Duration.ofMinutes(30));
    }
  }

  @Test
  void reusesAllValidResultsWithoutAiOrExtendingExpiry() {
    var savedResults =
        List.of(saved(impact("삼성전자", ImpactLevel.LOW)), saved(impact("SK하이닉스", ImpactLevel.HIGH)));
    given(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                eq(List.of("삼성전자", "SK하이닉스")),
                eq("asset-v4-grounded-source"),
                eq("test-model"),
                any()))
        .willReturn(savedResults);

    var response = service.analyze(targets);

    verifyNoInteractions(ai);
    assertSummary(2, 0, 0, "SUCCESS");
    verify(repository, never())
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(response.impacts()).extracting(AssetImpactResult::sortOrder).containsExactly(1, 2);
    assertThat(response.sources()).hasSize(1);
    assertThat(response.overallImpact()).isEmpty();
  }

  @Test
  void analyzesOnlyMissingAssetsAndMergesWithSavedResults() {
    var savedResult = saved(impact("삼성전자", ImpactLevel.HIGH));
    given(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                anyList(), anyString(), anyString(), any()))
        .willReturn(List.of(savedResult));
    given(ai.analyze(List.of(targets.get(1))))
        .willReturn(result(impact("SK하이닉스", ImpactLevel.LOW)));

    var response = service.analyze(targets);

    verify(ai).analyze(List.of(targets.get(1)));
    assertSummary(1, 1, 0, "SUCCESS");
    verifyNoMoreInteractions(ai);
    verify(repository, never())
        .upsert(eq("삼성전자"), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("삼성전자", "SK하이닉스");
  }

  @Test
  void doesNotStoreInsufficientEvidenceButPreservesPartialResponse() {
    var insufficient =
        new AssetImpactResult(
            "삼성전자", null, null, "근거 부족이에요.", "", "", "근거 부족이에요.", "", List.of(), 1);
    given(ai.analyze(targets)).willReturn(result(insufficient, impact("SK하이닉스", ImpactLevel.HIGH)));

    var response = service.analyze(targets);

    verify(repository, never())
        .upsert(eq("삼성전자"), anyString(), anyString(), anyString(), any(), any());
    verify(repository).upsert(eq("SK하이닉스"), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts()).hasSize(2);
    assertThat(response.impacts().getLast().direction()).isNull();
  }

  @Test
  void failedAiDoesNotStoreResults() {
    given(ai.analyze(targets)).willThrow(new IllegalStateException("외부 호출 실패"));
    assertThatThrownBy(() -> service.analyze(targets)).isInstanceOf(IllegalStateException.class);
    assertSummary(0, 2, 0, "FAILED");
    verify(repository, never())
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void emptyTargetsDoNotCallDatabaseOrAi() {
    assertThat(service.analyze(List.of()).impacts()).isEmpty();
    verifyNoInteractions(repository, ai);
  }

  @ParameterizedTest
  @ValueSource(ints = {9, 50})
  void concurrentRequestsShareOneAnalysisAndKeepTheirOwnOrdering(int requestCount)
      throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    given(ai.analyze(targets))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.HIGH));
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var owner = executor.submit(() -> service.analyze(targets));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var waiting = new ArrayList<FutureTask<PortfolioAnalysisResult>>();
        for (int i = 0; i < requestCount - 1; i++) {
          var task = new FutureTask<>(() -> service.analyze(targets.reversed()));
          waiting.add(task);
          awaitWaiting(Thread.ofPlatform().start(task));
        }
        release.countDown();
        assertThat(owner.get(5, TimeUnit.SECONDS).impacts())
            .extracting(AssetImpactResult::assetName)
            .containsExactly("삼성전자", "SK하이닉스");
        for (var task : waiting) {
          var response = task.get(5, TimeUnit.SECONDS);
          assertThat(response.impacts())
              .extracting(AssetImpactResult::assetName)
              .containsExactly("SK하이닉스", "삼성전자");
          assertThat(response.impacts())
              .extracting(AssetImpactResult::sortOrder)
              .containsExactly(1, 2);
        }
      } finally {
        release.countDown();
      }
    }
    verify(ai).analyze(targets);
    assertThat(appender.list).hasSize(requestCount);
    assertThat(
            appender.list.stream()
                .map(this::fields)
                .filter(values -> Integer.valueOf(2).equals(values.get("sharedCount"))))
        .hasSize(requestCount - 1)
        .allSatisfy(
            values ->
                assertThat(values)
                    .containsEntry("newAnalysisCount", 0)
                    .containsEntry("outcome", "SUCCESS"));
    verify(repository, times(2))
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void failedSaveDoesNotLeaveAnInFlightEntry() {
    given(ai.analyze(targets))
        .willReturn(result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.LOW)));
    doThrow(new IllegalStateException("저장 실패"))
        .doNothing()
        .when(repository)
        .upsert(eq("삼성전자"), anyString(), anyString(), anyString(), any(), any());
    assertThatThrownBy(() -> service.analyze(targets)).isInstanceOf(IllegalStateException.class);
    assertThat(service.analyze(targets).impacts()).hasSize(2);
    verify(ai, times(2)).analyze(targets);
  }

  @Test
  void overlappingRequestsShareCommonStockAndAnalyzeOnlyTheirOwnMissingStocks() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var otherAnalyzed = new CountDownLatch(1);
    var otherTarget = new PortfolioAnalysisTarget("현대차");
    given(ai.analyze(targets))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.LOW));
            });
    given(ai.analyze(List.of(otherTarget)))
        .willAnswer(
            invocation -> {
              otherAnalyzed.countDown();
              return result(impact("현대차", ImpactLevel.HIGH));
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.analyze(targets));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> service.analyze(List.of(targets.get(1), otherTarget)));
        assertThat(otherAnalyzed.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).impacts()).hasSize(2);
        assertThat(second.get(5, TimeUnit.SECONDS).impacts())
            .extracting(AssetImpactResult::assetName)
            .containsExactly("현대차", "SK하이닉스");
      } finally {
        release.countDown();
      }
    }
    verify(ai).analyze(targets);
    verify(ai).analyze(List.of(otherTarget));
    verifyNoMoreInteractions(ai);
    verify(repository, times(3))
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void failureReachesWaitersAndNextRequestCanRetry() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var failure = new IllegalStateException("외부 호출 실패");
    given(ai.analyze(targets))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              throw failure;
            })
        .willReturn(result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.LOW)));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.analyze(targets));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var second = new FutureTask<>(() -> service.analyze(targets));
        var waiter = Thread.ofPlatform().start(second);
        awaitWaiting(waiter);
        release.countDown();
        assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(service.analyze(targets).impacts()).hasSize(2);
      } finally {
        release.countDown();
      }
    }
    verify(ai, times(2)).analyze(targets);
  }

  @Test
  void timedOutWaiterDoesNotCancelOwnerOrStartDuplicateAnalysis() throws Exception {
    service =
        new PortfolioAssetAnalysisService(
            repository, ai, "test-model", Duration.ofMinutes(30), Duration.ofMillis(20));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    given(ai.analyze(targets))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.LOW));
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var owner = executor.submit(() -> service.analyze(targets));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.analyze(targets)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.analyze(targets)).isInstanceOf(BusinessException.class);
        release.countDown();
        assertThat(owner.get(5, TimeUnit.SECONDS).impacts()).hasSize(2);
      } finally {
        release.countDown();
      }
    }
    verify(ai).analyze(targets);
  }

  @Test
  void doesNotRestartWaitBudgetAfterAnalyzingOwnedAssets() throws Exception {
    service =
        new PortfolioAssetAnalysisService(
            repository, ai, "test-model", Duration.ofMinutes(30), Duration.ofMillis(500));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var ownAnalysisFinished = new CountDownLatch(1);
    var common = targets.getFirst();
    var own = targets.getLast();
    given(ai.analyze(List.of(common)))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return result(impact(common.assetName(), ImpactLevel.HIGH));
            });
    given(ai.analyze(List.of(own)))
        .willAnswer(
            invocation -> {
              Thread.sleep(600);
              ownAnalysisFinished.countDown();
              return result(impact(own.assetName(), ImpactLevel.LOW));
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var owner = executor.submit(() -> service.analyze(List.of(common)));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var mixed = executor.submit(() -> service.analyze(targets));
        assertThat(ownAnalysisFinished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> mixed.get(250, TimeUnit.MILLISECONDS))
            .hasCauseInstanceOf(BusinessException.class);
        release.countDown();
        assertThat(owner.get(5, TimeUnit.SECONDS).impacts()).hasSize(1);
      } finally {
        release.countDown();
      }
    }
    verify(ai).analyze(List.of(common));
    verify(ai).analyze(List.of(own));
    verifyNoMoreInteractions(ai);
  }

  @Test
  void interruptedWaiterKeepsInterruptFlagAndDoesNotCancelOwner() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    given(ai.analyze(targets))
        .willAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return result(impact("삼성전자", ImpactLevel.HIGH), impact("SK하이닉스", ImpactLevel.LOW));
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var owner = executor.submit(() -> service.analyze(targets));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var second =
            new FutureTask<>(
                () -> {
                  assertThatThrownBy(() -> service.analyze(targets))
                      .isInstanceOf(BusinessException.class);
                  return Thread.currentThread().isInterrupted();
                });
        var waiter = Thread.ofPlatform().start(second);
        awaitWaiting(waiter);
        waiter.interrupt();
        assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(owner.get(5, TimeUnit.SECONDS).impacts()).hasSize(2);
      } finally {
        release.countDown();
      }
    }
    verify(ai).analyze(targets);
  }

  @Test
  void rechecksSavedResultsAfterTakingOwnershipToAvoidDuplicateAnalysis() {
    var completed =
        List.of(saved(impact("삼성전자", ImpactLevel.HIGH)), saved(impact("SK하이닉스", ImpactLevel.LOW)));
    given(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                anyList(), anyString(), anyString(), any()))
        .willReturn(List.of())
        .willReturn(completed);
    assertThat(service.analyze(targets).impacts()).hasSize(2);
    verifyNoInteractions(ai);
    assertSummary(2, 0, 0, "SUCCESS");
  }

  private Map<String, Object> fields(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }

  private void assertSummary(int hits, int generated, int shared, String outcome) {
    assertThat(appender.list).hasSize(1);
    var values = fields(appender.list.getFirst());
    assertThat(values)
        .containsEntry("event", "portfolio.analysis.cache.completed")
        .containsEntry("cacheHitCount", hits)
        .containsEntry("newAnalysisCount", generated)
        .containsEntry("sharedCount", shared)
        .containsEntry("outcome", outcome);
    assertThat((Long) values.get("sharedWaitMs")).isNotNegative();
    assertThat(values.toString()).doesNotContain("삼성전자", "SK하이닉스", "Revenue increased");
  }

  private void awaitWaiting(Thread thread) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertThat(thread.getState()).isEqualTo(Thread.State.TIMED_WAITING);
  }

  private PortfolioAssetAnalysis saved(AssetImpactResult impact) {
    var saved = mock(PortfolioAssetAnalysis.class);
    given(saved.getAssetName()).willReturn(impact.assetName());
    given(saved.getSnapshot()).willReturn(impact);
    return saved;
  }

  private PortfolioAnalysisResult result(AssetImpactResult... impacts) {
    return new PortfolioAnalysisResult("", List.of(impacts), List.of());
  }

  private AssetImpactResult impact(String name, ImpactLevel level) {
    var source =
        new PortfolioAnalysisResult.Source(
            "실적 발표",
            "https://example.com/earnings",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 1),
            "매출이 늘었어요.",
            "Revenue increased.");
    return new AssetImpactResult(
        name,
        ImpactDirection.POSITIVE,
        level,
        "매출이 늘었어요.",
        "실적에 유리해요.",
        "다음 실적을 확인해요.",
        "매출 증가를 반영했어요.",
        source.excerpt(),
        List.of(source),
        9);
  }
}
