package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;

public record PortfolioAnalysisResponse(
    @Schema(description = "요청으로 전달받은 전체 포트폴리오 자산 수", example = "7") int totalAssetCount,
    @Schema(description = "실제 분석된 자산 수", example = "3") int analyzedAssetCount,
    @Schema(description = "전체 근거 확보 시 영향 방향. 일부라도 근거 부족이면 null", example = "POSITIVE")
        ImpactDirection overallDirection,
    @Schema(
            description = "전체 영향 점수(-100~100). 일부라도 근거 부족이면 null. 예상 수익률이나 확률이 아님",
            example = "61.00")
        BigDecimal overallScore,
    @Schema(description = "긍정 자산의 보유 비중과 영향 수준을 반영한 기여 점수(0~100)", example = "70.00")
        BigDecimal positiveImpactScore,
    @Schema(description = "부정 자산의 보유 비중과 영향 수준을 반영한 기여 점수(0~100)", example = "9.00")
        BigDecimal negativeImpactScore,
    @Schema(description = "분석 생성 시각. 분석 결과가 없으면 null", nullable = true) Instant analyzedAt,
    @Schema(description = "전체 자산 구성·집중도 → 비중과 근거를 고려한 종합 영향 → 우선 확인 사항의 세 문단. 일부 근거 부족 시 판단 범위를 명시")
        String overallImpact,
    @Schema(description = "포트폴리오 자산별 주요 이슈 영향 분석 목록") List<PortfolioAssetImpactResponse> impacts,
    @Schema(description = "분석에 사용된 웹 검색 출처") List<PortfolioAnalysisSourceResponse> sources) {

  public PortfolioAnalysisResponse {
    impacts = List.copyOf(impacts);
    sources = List.copyOf(sources);
  }

  @com.fasterxml.jackson.annotation.JsonProperty
  @Schema(description = "전체 자산 중 근거를 확보한 보유 비중(0~100). 미확보 비중을 중립으로 집계하지 않습니다.")
  public BigDecimal analyzedWeight() {
    return impacts.stream()
        .filter(impact -> impact.direction() != null)
        .map(PortfolioAssetImpactResponse::weight)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @com.fasterxml.jackson.annotation.JsonProperty
  @Schema(description = "COMPLETE: 전체 근거 확보, PARTIAL: 일부 부족, INSUFFICIENT: 전체 부족")
  public String coverage() {
    long supported = impacts.stream().filter(impact -> impact.direction() != null).count();
    return supported == 0 ? "INSUFFICIENT" : supported == totalAssetCount ? "COMPLETE" : "PARTIAL";
  }

  public PortfolioAnalysisResponse withOverallImpact(String text) {
    return new PortfolioAnalysisResponse(
        totalAssetCount,
        analyzedAssetCount,
        overallDirection,
        overallScore,
        positiveImpactScore,
        negativeImpactScore,
        analyzedAt,
        text,
        impacts,
        sources);
  }

  public PortfolioAnalysisSnapshot toSnapshot(List<PortfolioAssetRequest> assets) {
    return new PortfolioAnalysisSnapshot(
        assets.stream()
            .map(
                asset ->
                    new PortfolioAnalysisSnapshot.Asset(
                        asset.assetName(), asset.weight(), asset.quantity()))
            .toList(),
        new PortfolioImpactSummary(
            overallDirection, overallScore, positiveImpactScore, negativeImpactScore),
        overallImpact,
        impacts.stream()
            .map(
                impact ->
                    new PortfolioAnalysisSnapshot.AssetImpact(
                        impact.assetName(),
                        impact.direction(),
                        impact.impactLevel(),
                        impact.issueSummary(),
                        impact.expectedReaction(),
                        impact.outlook(),
                        impact.reason(),
                        impact.evidenceSentence(),
                        impact.rank()))
            .toList());
  }

  public static PortfolioAnalysisResponse from(
      PortfolioAnalysis analysis, List<PortfolioAnalysisEvidence> evidence) {
    PortfolioAnalysisSnapshot snapshot = analysis.getSnapshot();
    Map<String, PortfolioAnalysisSnapshot.Asset> assets =
        snapshot.assets().stream()
            .collect(
                Collectors.toMap(PortfolioAnalysisSnapshot.Asset::assetName, Function.identity()));
    List<PortfolioAssetImpactResponse> impacts =
        snapshot.impacts().stream()
            .map(
                impact ->
                    new PortfolioAssetImpactResponse(
                        impact.assetName(),
                        assets.get(impact.assetName()).weight(),
                        impact.direction(),
                        impact.impactLevel(),
                        impact.issueSummary(),
                        impact.expectedReaction(),
                        impact.outlook(),
                        impact.reason(),
                        impact.evidenceSentence(),
                        evidence.stream()
                            .filter(source -> impact.assetName().equals(source.getAssetName()))
                            .map(
                                source ->
                                    new PortfolioAnalysisSourceResponse(
                                        source.getTitle(),
                                        source.getUrl(),
                                        source.getPublishedOn(),
                                        source.getOccurredOn(),
                                        source.getExcerpt(),
                                        source.getFactQuote()))
                            .distinct()
                            .toList(),
                        impact.rank()))
            .toList();
    return of(
        snapshot.assets().size(),
        snapshot.summary(),
        analysis.getAnalyzedAt(),
        snapshot.overallImpact(),
        impacts,
        evidence.stream()
            .map(
                source ->
                    new PortfolioAnalysisSourceResponse(
                        source.getTitle(),
                        source.getUrl(),
                        source.getPublishedOn(),
                        source.getOccurredOn(),
                        source.getExcerpt(),
                        source.getFactQuote()))
            .collect(
                Collectors.toMap(
                    PortfolioAnalysisSourceResponse::url,
                    Function.identity(),
                    (first, ignored) -> first,
                    java.util.LinkedHashMap::new))
            .values()
            .stream()
            .toList());
  }

  public static PortfolioAnalysisResponse of(
      int totalAssetCount,
      PortfolioImpactSummary impactSummary,
      Instant analyzedAt,
      String overallImpact,
      List<PortfolioAssetImpactResponse> impacts,
      List<PortfolioAnalysisSourceResponse> sources) {
    return new PortfolioAnalysisResponse(
        totalAssetCount,
        (int) impacts.stream().filter(impact -> impact.direction() != null).count(),
        impacts.size() == totalAssetCount ? impactSummary.direction() : null,
        impacts.size() == totalAssetCount ? impactSummary.score() : null,
        impactSummary.positiveScore(),
        impactSummary.negativeScore(),
        analyzedAt,
        overallImpact,
        impacts,
        sources);
  }

  public static PortfolioAnalysisResponse empty() {
    return new PortfolioAnalysisResponse(
        0, 0, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, "", List.of(), List.of());
  }
}
