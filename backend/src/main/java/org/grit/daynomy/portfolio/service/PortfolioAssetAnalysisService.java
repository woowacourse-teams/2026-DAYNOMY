package org.grit.daynomy.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.AssetImpactResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.repository.PortfolioAssetAnalysisRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAssetAnalysisService {
  static final String ANALYSIS_VERSION = "asset-v2-batch5";
  private final PortfolioAssetAnalysisRepository repository;
  private final PortfolioAnalysisAiClient ai;
  private final String model;
  private final Duration ttl;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  // 모델과 분석 버전은 이 서비스 인스턴스에서 고정이므로 종목명으로 진행 중 작업을 구분한다.
  private final Map<String, CompletableFuture<AssetImpactResult>> inFlight = new HashMap<>();
  private final Duration waitTimeout;

  @Autowired
  public PortfolioAssetAnalysisService(
      PortfolioAssetAnalysisRepository repository,
      PortfolioAnalysisAiClient ai,
      @Value("${ai.openai.model:gpt-5-mini}") String model,
      @Value("${portfolio.analysis.cache-ttl:30m}") Duration ttl) {
    this(repository, ai, model, ttl, Duration.ofMinutes(4));
  }

  PortfolioAssetAnalysisService(
      PortfolioAssetAnalysisRepository repository,
      PortfolioAnalysisAiClient ai,
      String model,
      Duration ttl,
      Duration waitTimeout) {
    this.waitTimeout = waitTimeout;
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("종목 분석 유효기간은 0보다 커야 합니다.");
    }
    this.repository = repository;
    this.ai = ai;
    this.model = model;
    this.ttl = ttl;
  }

  // 신규 검색 없이 현재 모델·버전의 유효한 공통 분석만 선별 신호로 사용한다.
  public Map<String, org.grit.daynomy.market.domain.asset.ImpactLevel> cachedImpactLevels(
      List<String> assetNames) {
    return repository
        .findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
            assetNames, ANALYSIS_VERSION, model, Instant.now())
        .stream()
        .filter(
            saved ->
                saved.getSnapshot().direction() != null
                    && saved.getSnapshot().impactLevel() != null
                    && !saved.getSnapshot().sources().isEmpty())
        .collect(
            java.util.stream.Collectors.toMap(
                saved -> saved.getAssetName(), saved -> saved.getSnapshot().impactLevel()));
  }

  // 외부 AI 호출 중 DB 트랜잭션을 유지하지 않는다.
  public PortfolioAnalysisResult analyze(List<PortfolioAnalysisTarget> targets) {
    if (targets.isEmpty()) return new PortfolioAnalysisResult("", List.of(), List.of());
    long deadline = System.nanoTime() + waitTimeout.toNanos();
    var results = new HashMap<String, AssetImpactResult>();
    var freshSources = new ArrayList<PortfolioAnalysisResult.Source>();
    repository
        .findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
            targets.stream().map(PortfolioAnalysisTarget::assetName).toList(),
            ANALYSIS_VERSION,
            model,
            Instant.now())
        .forEach(saved -> results.put(saved.getAssetName(), saved.getSnapshot()));
    var missing =
        targets.stream().filter(target -> !results.containsKey(target.assetName())).toList();
    var owned = new LinkedHashMap<String, CompletableFuture<AssetImpactResult>>();
    var shared = new LinkedHashMap<String, CompletableFuture<AssetImpactResult>>();
    // 등록만 잠그고 DB 조회, AI 호출, 결과 대기는 잠금 밖에서 수행한다.
    synchronized (inFlight) {
      for (var target : missing) {
        var future = inFlight.get(target.assetName());
        if (future == null) {
          future = new CompletableFuture<>();
          inFlight.put(target.assetName(), future);
          owned.put(target.assetName(), future);
        }
        shared.put(target.assetName(), future);
      }
    }
    if (!owned.isEmpty()) {
      try {
        // 최초 조회 이후 다른 요청이 저장하고 진행 중 표시를 지웠을 수 있다.
        repository
            .findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                List.copyOf(owned.keySet()), ANALYSIS_VERSION, model, Instant.now())
            .forEach(saved -> results.put(saved.getAssetName(), saved.getSnapshot()));
        var toAnalyze =
            owned.keySet().stream()
                .filter(name -> !results.containsKey(name))
                .map(PortfolioAnalysisTarget::new)
                .toList();
        if (!toAnalyze.isEmpty()) {
          var generated = ai.analyze(toAnalyze);
          freshSources.addAll(generated.sources());
          Instant analyzedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
          for (var impact : generated.impacts()) {
            results.put(impact.assetName(), impact);
            if (impact.direction() != null
                && impact.impactLevel() != null
                && !impact.sources().isEmpty()) {
              save(impact, analyzedAt);
            }
          }
        }
        for (var name : owned.keySet()) {
          if (!results.containsKey(name)) {
            throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
          }
        }
        owned.forEach((name, future) -> future.complete(results.get(name)));
      } catch (RuntimeException | Error exception) {
        owned.values().forEach(future -> future.completeExceptionally(exception));
        throw exception;
      } finally {
        synchronized (inFlight) {
          owned.forEach((name, future) -> inFlight.remove(name, future));
        }
      }
    }
    // 먼저 자신이 맡은 분석을 완료해야 서로 다른 종목을 기다리는 요청끼리 교착되지 않는다.
    for (var entry : shared.entrySet()) {
      results.put(entry.getKey(), await(entry.getValue(), deadline));
    }
    var ordered =
        targets.stream()
            .map(target -> results.get(target.assetName()))
            .sorted(Comparator.comparingInt(impact -> priority(impact.impactLevel())))
            .toList();
    var ranked = new ArrayList<AssetImpactResult>();
    for (int index = 0; index < ordered.size(); index++) {
      ranked.add(withOrder(ordered.get(index), index + 1));
    }
    var sources =
        java.util.stream.Stream.concat(
                ranked.stream().flatMap(impact -> impact.sources().stream()), freshSources.stream())
            .distinct()
            .toList();
    return new PortfolioAnalysisResult("", ranked, sources);
  }

  private AssetImpactResult await(CompletableFuture<AssetImpactResult> future, long deadline) {
    try {
      return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } catch (TimeoutException exception) {
      // 기다리는 요청만 종료한다. 진행 중인 원래 작업을 지우면 중복 AI 호출이 발생한다.
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) throw cause;
      if (exception.getCause() instanceof Error cause) throw cause;
      throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
    }
  }

  private void save(AssetImpactResult impact, Instant analyzedAt) {
    try {
      // 순위는 요청된 포트폴리오마다 다시 계산하며 공통 결과에는 저장하지 않는다.
      repository.upsert(
          impact.assetName(),
          ANALYSIS_VERSION,
          model,
          mapper.writeValueAsString(withOrder(impact, 0)),
          analyzedAt,
          analyzedAt.plus(ttl));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("종목 분석 결과를 저장 형식으로 변환하지 못했습니다.", exception);
    }
  }

  private AssetImpactResult withOrder(AssetImpactResult impact, int order) {
    return new AssetImpactResult(
        impact.assetName(),
        impact.direction(),
        impact.impactLevel(),
        impact.issueSummary(),
        impact.expectedReaction(),
        impact.outlook(),
        impact.reason(),
        impact.evidenceSentence(),
        impact.sources(),
        order);
  }

  private int priority(ImpactLevel level) {
    if (level == null) return 4;
    return switch (level) {
      case HIGH -> 1;
      case MEDIUM -> 2;
      case LOW -> 3;
    };
  }
}
