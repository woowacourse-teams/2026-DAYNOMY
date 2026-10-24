package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

class PortfolioAnalysisBatchesTest {
  @Test
  void processesFiftyAssetsInFiveAssetBatchesWithAtMostTwoConcurrentCalls() {
    var targets = IntStream.range(0, 50).boxed().toList();
    var batches = new ConcurrentLinkedQueue<List<Integer>>();
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    var barrier = new CyclicBarrier(2);

    var result =
        PortfolioAnalysisBatches.analyze(
            targets,
            batch -> {
              batches.add(batch);
              maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
              try {
                barrier.await(5, TimeUnit.SECONDS);
                return new PortfolioAnalysisResult(
                    "", batch.stream().map(this::impact).toList(), List.of());
              } catch (Exception exception) {
                throw new AssertionError(exception);
              } finally {
                active.decrementAndGet();
              }
            });

    assertThat(batches).hasSize(10).allSatisfy(batch -> assertThat(batch).hasSize(5));
    assertThat(batches.stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(targets);
    assertThat(maximum.get()).isEqualTo(2);
    assertThat(result.impacts()).hasSize(50);
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(1, 50).boxed().toList());
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::impactLevel)
        .isSortedAccordingTo(java.util.Comparator.naturalOrder());
  }

  @Test
  void stopsBeforeStartingTheNextBatchesWhenOneBatchFails() {
    var requests = new AtomicInteger();
    var bothStarted = new CyclicBarrier(2);
    var failure = new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    assertThatThrownBy(
            () ->
                PortfolioAnalysisBatches.analyze(
                    IntStream.range(0, 20).boxed().toList(),
                    batch -> {
                      requests.incrementAndGet();
                      try {
                        bothStarted.await(5, TimeUnit.SECONDS);
                      } catch (Exception exception) {
                        throw new AssertionError(exception);
                      }
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(requests.get()).isEqualTo(2);
  }

  @Test
  void startsTheNextBatchWithoutWaitingForTheOtherSlowBatch() {
    var releaseSlowBatch = new CountDownLatch(1);
    var result =
        PortfolioAnalysisBatches.analyze(
            IntStream.range(0, 15).boxed().toList(),
            batch -> {
              try {
                if (batch.getFirst() == 0)
                  assertThat(releaseSlowBatch.await(5, TimeUnit.SECONDS)).isTrue();
                if (batch.getFirst() == 10) releaseSlowBatch.countDown();
                return new PortfolioAnalysisResult(
                    "", batch.stream().map(this::impact).toList(), List.of());
              } catch (InterruptedException exception) {
                throw new AssertionError(exception);
              }
            });
    assertThat(result.impacts()).hasSize(15);
    assertThat(releaseSlowBatch.getCount()).isZero();
  }

  @Test
  void cancelsRunningWorkAndDoesNotStartMoreBatchesAfterTheTotalDeadline() throws Exception {
    var running = new AtomicInteger();
    var stopped = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                PortfolioAnalysisBatches.analyze(
                    IntStream.range(0, 50).boxed().toList(),
                    batch -> {
                      running.incrementAndGet();
                      try {
                        release.await(5, TimeUnit.SECONDS);
                        return new PortfolioAnalysisResult(
                            "", batch.stream().map(this::impact).toList(), List.of());
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
                      } finally {
                        stopped.countDown();
                      }
                    },
                    Duration.ofMillis(300)))
        .isInstanceOf(BusinessException.class);
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
    assertThat(running.get()).isEqualTo(2);
    assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void preservesTheResultForASingleSmallBatch() {
    var result = new PortfolioAnalysisResult("", List.of(impact(1)), List.of());
    assertThat(PortfolioAnalysisBatches.analyze(List.of(1), batch -> result)).isSameAs(result);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 6})
  void keepsRequestContextAndAssignsBatchNumbers(int targetCount) {
    MDC.put("requestId", "batch-test-request");
    MDC.put("batchNumber", "parent");
    try {
      PortfolioAnalysisBatches.analyze(
          IntStream.range(0, targetCount).boxed().toList(),
          batch -> {
            assertThat(MDC.get("requestId")).isEqualTo("batch-test-request");
            assertThat(MDC.get("batchNumber"))
                .isEqualTo(Integer.toString(batch.getFirst() / 5 + 1));
            return new PortfolioAnalysisResult(
                "", batch.stream().map(this::impact).toList(), List.of());
          });
      assertThat(MDC.get("requestId")).isEqualTo("batch-test-request");
      assertThat(MDC.get("batchNumber")).isEqualTo("parent");
    } finally {
      MDC.remove("requestId");
      MDC.remove("batchNumber");
    }
  }

  @Test
  void propagatesTheCurrentObservationToWorkerThreads() {
    var registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(new RecordingObservationHandler());
    var observation = Observation.start("portfolio.analysis.test", registry);
    try (var scope = observation.openScope()) {
      PortfolioAnalysisBatches.analyze(
          IntStream.range(0, 6).boxed().toList(),
          batch -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return new PortfolioAnalysisResult(
                "", batch.stream().map(this::impact).toList(), List.of());
          });
    } finally {
      observation.stop();
    }
  }

  private static class RecordingObservationHandler
      implements ObservationHandler<Observation.Context> {
    @Override
    public boolean supportsContext(Observation.Context context) {
      return true;
    }
  }

  private PortfolioAnalysisResult.AssetImpactResult impact(int index) {
    return new PortfolioAnalysisResult.AssetImpactResult(
        "종목" + index,
        ImpactDirection.NEUTRAL,
        ImpactLevel.values()[index % 3],
        "관련 이슈예요.",
        "추가 확인이 필요해요.",
        "실적을 확인해야 해요.",
        "관련 근거예요.",
        "원문 근거예요.",
        List.of(),
        1);
  }
}
