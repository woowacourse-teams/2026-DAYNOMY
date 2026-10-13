package org.grit.daynomy.portfolio.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck.Outcome;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison.Fact;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;

/** 근거가 같은 종목은 설명·판단을 그대로 보존한 뒤 전체 결과를 다시 집계한다. */
final class PortfolioAnalysisRefresh {
  private PortfolioAnalysisRefresh() {}

  record Revision(Outcome outcome, PortfolioAnalysisResponse analysis, String reason) {}

  static Revision compare(PortfolioAnalysisResponse previous, PortfolioAnalysisResponse candidate) {
    var byName =
        candidate.impacts().stream()
            .collect(
                Collectors.toMap(PortfolioAssetImpactResponse::assetName, Function.identity()));
    List<PortfolioAssetImpactResponse> merged = new ArrayList<>();
    List<String> reasons = new ArrayList<>();
    for (var old : previous.impacts()) {
      var current = byName.get(old.assetName());
      if (current == null) {
        return new Revision(Outcome.UNCONFIRMED, previous, "");
      }
      var comparison = PortfolioEvidenceComparison.compare(facts(old), facts(current));
      if (comparison == PortfolioEvidenceComparison.Result.UNCONFIRMED) {
        return new Revision(Outcome.UNCONFIRMED, previous, "");
      }
      if (comparison == PortfolioEvidenceComparison.Result.SAME) {
        merged.add(old);
      } else {
        merged.add(current);
        reasons.add(current.assetName() + ": " + current.evidenceSentence());
      }
    }
    if (reasons.isEmpty()) {
      return new Revision(Outcome.UNCHANGED, previous, "");
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
            .thenComparing(PortfolioAssetImpactResponse::weight, Comparator.reverseOrder())
            .thenComparing(PortfolioAssetImpactResponse::assetName));
    for (int index = 0; index < merged.size(); index++) {
      var impact = merged.get(index);
      merged.set(
          index,
          new PortfolioAssetImpactResponse(
              impact.assetName(),
              impact.weight(),
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
    // 새 전체 설명이 근거 없는 다른 종목의 판단 변경을 다시 끌어들이지 않도록 보존된 본문으로 구성한다.
    var major =
        merged.stream()
            .sorted(Comparator.comparing(PortfolioAssetImpactResponse::weight).reversed())
            .limit(3)
            .toList();
    String prose =
        List.of(
                paragraph(major, PortfolioAssetImpactResponse::issueSummary),
                paragraph(major, PortfolioAssetImpactResponse::expectedReaction),
                paragraph(major, PortfolioAssetImpactResponse::outlook))
            .stream()
            .filter(text -> !text.isBlank())
            .collect(Collectors.joining("\n\n"));
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
        PortfolioAnalysisResponse.of(
            previous.totalAssetCount(), summary, candidate.analyzedAt(), prose, merged, sources),
        "새 근거 반영 — " + String.join(" ", reasons));
  }

  private static List<Fact> facts(PortfolioAssetImpactResponse impact) {
    return impact.sources().stream()
        .map(
            source ->
                new Fact(source.url(), source.occurredOn(), source.excerpt(), source.factQuote()))
        .toList();
  }

  private static String paragraph(
      List<PortfolioAssetImpactResponse> impacts,
      Function<PortfolioAssetImpactResponse, String> field) {
    return impacts.stream()
        .filter(impact -> !field.apply(impact).isBlank())
        .map(impact -> impact.assetName() + ": " + field.apply(impact))
        .collect(Collectors.joining(" "));
  }
}
