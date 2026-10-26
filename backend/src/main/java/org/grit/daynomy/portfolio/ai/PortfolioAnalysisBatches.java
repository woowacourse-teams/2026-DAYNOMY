package org.grit.daynomy.portfolio.ai;

import io.micrometer.context.ContextSnapshotFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.slf4j.MDC;

/** V1·V2에 동일한 요청 크기와 병렬 처리 기준을 적용한다. */
@Slf4j
public final class PortfolioAnalysisBatches {
  private static final int BATCH_SIZE = 5;
  private static final int CONCURRENCY = 2;

  private PortfolioAnalysisBatches() {}

  public static <T> PortfolioAnalysisResult analyze(
      List<T> targets, Function<List<T>, PortfolioAnalysisResult> request) {
    return analyze(targets, request, BATCH_SIZE, Duration.ofMinutes(4));
  }

  static <T> PortfolioAnalysisResult analyze(
      List<T> targets, Function<List<T>, PortfolioAnalysisResult> request, Duration timeout) {
    return analyze(targets, request, BATCH_SIZE, timeout);
  }

  public static <T> PortfolioAnalysisResult analyze(
      List<T> targets, Function<List<T>, PortfolioAnalysisResult> request, int batchSize) {
    return analyze(targets, request, batchSize, Duration.ofMinutes(4));
  }

  static void validateBatchSize(int batchSize) {
    if (batchSize < 1 || batchSize > BATCH_SIZE) {
      throw new IllegalArgumentException("분석 묶음 크기는 1~5여야 합니다.");
    }
  }

  private static <T> PortfolioAnalysisResult analyze(
      List<T> targets,
      Function<List<T>, PortfolioAnalysisResult> request,
      int batchSize,
      Duration timeout) {
    validateBatchSize(batchSize);
    if (targets.size() <= batchSize) {
      String previous = MDC.get("batchNumber");
      try {
        MDC.put("batchNumber", "1");
        return request.apply(targets);
      } finally {
        if (previous == null) MDC.remove("batchNumber");
        else MDC.put("batchNumber", previous);
      }
    }

    long deadline = System.nanoTime() + timeout.toNanos();
    var batches = new ArrayList<List<T>>();
    for (int start = 0; start < targets.size(); start += batchSize) {
      batches.add(List.copyOf(targets.subList(start, Math.min(start + batchSize, targets.size()))));
    }
    var completed = new TreeMap<Integer, PortfolioAnalysisResult>();
    var loggingContext = MDC.getCopyOfContextMap();
    var contextSnapshotFactory = ContextSnapshotFactory.builder().build();
    // 개별 분석은 선택된 최대 다섯 종목이 다른 종목의 완료를 기다리지 않게 한다.
    int concurrency = batchSize == 1 ? BATCH_SIZE : CONCURRENCY;
    var executor = Executors.newFixedThreadPool(concurrency);
    var completion = new ExecutorCompletionService<CompletedBatch>(executor);
    var futures = new ArrayList<Future<CompletedBatch>>();
    java.util.function.IntFunction<Future<CompletedBatch>> submit =
        index ->
            completion.submit(
                contextSnapshotFactory
                    .captureAll()
                    .wrap(
                        () -> {
                          var previous = MDC.getCopyOfContextMap();
                          try {
                            if (loggingContext != null) MDC.setContextMap(loggingContext);
                            MDC.put("batchNumber", Integer.toString(index + 1));
                            return new CompletedBatch(index, request.apply(batches.get(index)));
                          } finally {
                            if (previous == null) MDC.clear();
                            else MDC.setContextMap(previous);
                          }
                        }));
    try {
      int next = 0;
      while (next < Math.min(concurrency, batches.size())) futures.add(submit.apply(next++));
      while (completed.size() < batches.size()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new TimeoutException();
        var future = completion.poll(remaining, TimeUnit.NANOSECONDS);
        if (future == null) throw new TimeoutException();
        var batch = future.get();
        completed.put(batch.index(), batch.result());
        if (next < batches.size()) {
          if (System.nanoTime() >= deadline) throw new TimeoutException();
          futures.add(submit.apply(next++));
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } catch (TimeoutException exception) {
      log.atWarn()
          .addKeyValue("event", "portfolio.analysis.batch.timeout")
          .addKeyValue("targetCount", targets.size())
          .addKeyValue("completedBatchCount", completed.size())
          .log("종목 분석 전체 대기 시간 초과");
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) throw cause;
      if (exception.getCause() instanceof Error cause) throw cause;
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } finally {
      futures.forEach(future -> future.cancel(true));
      // 종료를 기다리면 개별 HTTP 대기 때문에 전체 제한 시간을 초과할 수 있다.
      executor.shutdownNow();
    }
    var results = completed.values();

    var ordered =
        results.stream()
            .flatMap(result -> result.impacts().stream())
            .sorted(
                Comparator.comparingInt(
                    impact ->
                        impact.impactLevel() == null
                            ? 4
                            : switch (impact.impactLevel()) {
                              case HIGH -> 1;
                              case MEDIUM -> 2;
                              case LOW -> 3;
                            }))
            .toList();
    var ranked = new ArrayList<PortfolioAnalysisResult.AssetImpactResult>();
    for (int index = 0; index < ordered.size(); index++) {
      var impact = ordered.get(index);
      ranked.add(
          new PortfolioAnalysisResult.AssetImpactResult(
              impact.assetName(),
              impact.direction(),
              impact.impactLevel(),
              impact.issueSummary(),
              impact.expectedReaction(),
              impact.outlook(),
              impact.reason(),
              impact.evidenceSentence(),
              impact.sources(),
              index + 1));
    }
    String summary =
        ranked.stream().noneMatch(impact -> impact.direction() != null)
            ? "판단에 필요한 근거를 확인하지 못했어요."
            : "";
    return new PortfolioAnalysisResult(
        summary,
        ranked,
        results.stream().flatMap(result -> result.sources().stream()).distinct().toList());
  }

  private record CompletedBatch(int index, PortfolioAnalysisResult result) {}
}
