package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck.Outcome;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison.Fact;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;

/** 근거가 같은 종목은 설명·판단을 그대로 보존한 뒤 전체 결과를 다시 집계한다. */
final class PortfolioAnalysisRefresh {
  private PortfolioAnalysisRefresh() {}

  record Revision(Outcome outcome, PortfolioAnalysisResponse analysis, String reason) {}

  static Revision compare(PortfolioAnalysisResponse previous, PortfolioAnalysisResponse candidate) {
    return compare(previous, candidate, null, Map.of());
  }

  static Revision compareSelected(
      PortfolioAnalysisResponse previous,
      PortfolioAnalysisResponse candidate,
      List<PortfolioAssetRequest> assets,
      Set<String> names) {
    return compare(
        previous,
        candidate,
        names,
        assets.stream()
            .collect(
                Collectors.toMap(PortfolioAssetRequest::assetName, PortfolioAssetRequest::weight)));
  }

  private static Revision compare(
      PortfolioAnalysisResponse previous,
      PortfolioAnalysisResponse candidate,
      Set<String> names,
      Map<String, BigDecimal> weights) {
    var previousNames =
        previous.impacts().stream()
            .map(PortfolioAssetImpactResponse::assetName)
            .collect(Collectors.toSet());
    boolean selectionChanged =
        names == null
            && (!previous.excludedAssets().isEmpty() || !candidate.excludedAssets().isEmpty())
            && !previousNames.equals(
                candidate.impacts().stream()
                    .map(PortfolioAssetImpactResponse::assetName)
                    .collect(Collectors.toSet()));
    var byName =
        candidate.impacts().stream()
            .collect(
                Collectors.toMap(PortfolioAssetImpactResponse::assetName, Function.identity()));
    List<PortfolioAssetImpactResponse> merged = new ArrayList<>();
    List<String> reasons = new ArrayList<>();
    boolean insufficient = false;
    for (var old : previous.impacts()) {
      if (names != null && !names.contains(old.assetName())) {
        merged.add(old);
        continue;
      }
      var current = byName.get(old.assetName());
      if (names != null && (current == null || current.direction() == null)) {
        insufficient = true;
        merged.add(old);
        continue;
      }
      if (current == null) {
        if (selectionChanged) continue;
        return new Revision(Outcome.UNCONFIRMED, previous, "");
      }
      // 근거 부족 종목은 보존하되 다른 종목의 새 근거 반영을 막지 않는다.
      if (old.sources().isEmpty() && current.sources().isEmpty()) {
        insufficient = true;
        merged.add(old);
        continue;
      }
      if (!validStoredImpact(old)) {
        merged.add(current);
        reasons.add(current.assetName());
        continue;
      }
      var comparison = PortfolioEvidenceComparison.compare(facts(old), facts(current));
      if (comparison == PortfolioEvidenceComparison.Result.UNCONFIRMED) {
        insufficient = true;
        merged.add(old);
        continue;
      }
      if (comparison == PortfolioEvidenceComparison.Result.SAME) {
        merged.add(old);
      } else {
        merged.add(current);
        reasons.add(current.assetName());
      }
    }
    if (selectionChanged) {
      candidate.impacts().stream()
          .filter(i -> !previousNames.contains(i.assetName()))
          .forEach(merged::add);
      reasons.add("중점 분석 대상");
    }
    boolean repairSummary =
        names == null
            && previous.analyzedAssetCount() > 0
            && !validSummary(previous)
            && candidate.analyzedAssetCount() > 0;
    if (reasons.isEmpty() && !repairSummary) {
      return new Revision(insufficient ? Outcome.UNCONFIRMED : Outcome.UNCHANGED, previous, "");
    }
    merged.sort(
        Comparator.comparingInt(
                (PortfolioAssetImpactResponse impact) ->
                    impact.impactLevel() == null
                        ? 3
                        : switch (impact.impactLevel()) {
                          case HIGH -> 0;
                          case MEDIUM -> 1;
                          case LOW -> 2;
                        })
            .thenComparing(
                impact -> weights.getOrDefault(impact.assetName(), impact.weight()),
                Comparator.reverseOrder())
            .thenComparing(PortfolioAssetImpactResponse::assetName));
    for (int index = 0; index < merged.size(); index++) {
      var impact = merged.get(index);
      merged.set(
          index,
          new PortfolioAssetImpactResponse(
              impact.assetName(),
              weights.getOrDefault(impact.assetName(), impact.weight()),
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
    var summary =
        PortfolioImpactSummary.calculate(
            merged.stream()
                .map(
                    impact ->
                        new PortfolioImpactSummary.AssetImpact(
                            impact.weight(), impact.direction(), impact.impactLevel()))
                .toList());
    // 외부 요약 호출은 트랜잭션 밖에서 최종 병합 결과로 수행한다.
    var sources =
        merged.stream()
            .flatMap(impact -> impact.sources().stream())
            .collect(
                Collectors.toMap(
                    source -> source.url(),
                    Function.identity(),
                    (first, ignored) -> first,
                    java.util.LinkedHashMap::new))
            .values()
            .stream()
            .toList();
    return new Revision(
        Outcome.UPDATED,
        new PortfolioAnalysisResponse(
            previous.totalAssetCount(),
            (int) merged.stream().filter(i -> i.direction() != null).count(),
            merged.size() == previous.totalAssetCount() ? summary.direction() : null,
            merged.size() == previous.totalAssetCount() ? summary.score() : null,
            summary.positiveScore(),
            summary.negativeScore(),
            candidate.analyzedAt(),
            "",
            merged,
            sources,
            names == null
                ? candidate.excludedAssets()
                : previous.excludedAssets().stream()
                    .map(
                        asset ->
                            new PortfolioAnalysisResponse.ExcludedAsset(
                                asset.assetName(),
                                weights.getOrDefault(asset.assetName(), asset.weight())))
                    .toList()),
        reasons.isEmpty()
            ? "종목별 판단은 유지하고 전체 설명을 정리했어요."
            : String.join(", ", reasons) + "의 근거와 설명을 갱신했어요.");
  }

  private static List<Fact> facts(PortfolioAssetImpactResponse impact) {
    return impact.sources().stream()
        .map(
            source ->
                new Fact(source.url(), source.occurredOn(), source.excerpt(), source.factQuote()))
        .toList();
  }

  private static boolean validStoredImpact(PortfolioAssetImpactResponse impact) {
    try {
      for (String text :
          List.of(
              impact.issueSummary(),
              impact.expectedReaction(),
              impact.outlook(),
              impact.reason(),
              impact.evidenceSentence())) {
        org.grit.daynomy.portfolio.ai.PortfolioAnalysisProse.normalize(text);
      }
      for (var source : impact.sources()) {
        if (org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourcePolicy.isIndex(source.url()))
          return false;
        org.grit.daynomy.portfolio.ai.PortfolioAnalysisProse.normalize(source.excerpt());
      }
      org.grit.daynomy.portfolio.ai.PortfolioAnalysisProse.requireSupportedDates(
          impact.issueSummary(),
          impact.sources().stream()
              .map(
                  source ->
                      source.excerpt()
                          + " "
                          + source.factQuote()
                          + " "
                          + source.publishedOn()
                          + " "
                          + source.occurredOn())
              .collect(Collectors.joining(" ")));
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static boolean validSummary(PortfolioAnalysisResponse analysis) {
    try {
      String[] paragraphs = analysis.overallImpact().split("\\R\\s*\\R");
      if (paragraphs.length != 3 || !paragraphs[0].startsWith("현재 보유한 ")) return false;
      for (String text : paragraphs) {
        org.grit.daynomy.portfolio.ai.PortfolioAnalysisProse.normalize(text);
        if (text.contains(":")) return false;
      }
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
