package org.grit.daynomy.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.AssetImpactResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.repository.PortfolioAssetAnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAssetAnalysisService {
  static final String ANALYSIS_VERSION = "asset-v1";
  private final PortfolioAssetAnalysisRepository repository;
  private final PortfolioAnalysisAiClient ai;
  private final String model;
  private final Duration ttl;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  public PortfolioAssetAnalysisService(
      PortfolioAssetAnalysisRepository repository,
      PortfolioAnalysisAiClient ai,
      @Value("${ai.openai.model:gpt-5-mini}") String model,
      @Value("${portfolio.analysis.cache-ttl:30m}") Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("종목 분석 유효기간은 0보다 커야 합니다.");
    }
    this.repository = repository;
    this.ai = ai;
    this.model = model;
    this.ttl = ttl;
  }

  // 외부 AI 호출 중 DB 트랜잭션을 유지하지 않는다.
  public PortfolioAnalysisResult analyze(List<PortfolioAnalysisTarget> targets) {
    if (targets.isEmpty()) return new PortfolioAnalysisResult("", List.of(), List.of());
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
    if (!missing.isEmpty()) {
      var generated = ai.analyze(missing);
      freshSources.addAll(generated.sources());
      Instant analyzedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
      for (var impact : generated.impacts()) {
        results.put(impact.assetName(), impact);
        // AI 클라이언트가 검증한 결과 중 판단 가능한 종목만 저장한다. 부분 실패는 재사용하지 않는다.
        if (impact.direction() != null
            && impact.impactLevel() != null
            && !impact.sources().isEmpty()) {
          save(impact, analyzedAt);
        }
      }
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
