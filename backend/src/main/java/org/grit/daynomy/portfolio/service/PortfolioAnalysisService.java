package org.grit.daynomy.portfolio.service;

import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisSourceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Primary
@Service
public class PortfolioAnalysisService {

  private final PortfolioAnalysisAiClient portfolioAnalysisAiClient;
  private final PortfolioAssetAnalysisService assetAnalysisService;
  private final org.grit.daynomy.portfolio.ai.PortfolioAnalysisNarrative narrative;
  private final GuestPortfolioAnalysisTrial guestPortfolioAnalysisTrial;

  @Observed(name = "portfolio.analysis", contextualName = "portfolio-analysis")
  public PortfolioAnalysisResponse analyze(
      PortfolioAnalysisRequest request, boolean authenticatedMember) {
    validateDistinctAssets(request.assets());
    validateTotalWeight(request.assets());

    if (request.assets().isEmpty()) {
      return PortfolioAnalysisResponse.empty();
    }

    if (!authenticatedMember) {
      return guestPortfolioAnalysisTrial.use(() -> analyze(request));
    }

    return analyze(request);
  }

  protected PortfolioAnalysisResponse analyze(PortfolioAnalysisRequest request) {
    return summarize(analyzeAssets(request));
  }

  protected PortfolioAnalysisResponse analyzeAssets(PortfolioAnalysisRequest request) {
    return response(request, assetAnalysisService.analyze(createTargets(request.assets())));
  }

  PortfolioAnalysisResponse summarize(PortfolioAnalysisResponse result) {
    return result.withOverallImpact(narrative.summarize(result));
  }

  @Observed(name = "portfolio.analysis.refresh", contextualName = "portfolio-analysis-refresh")
  public PortfolioAnalysisResponse refresh(
      PortfolioAnalysisRequest request, PortfolioAnalysisResponse previous) {
    validateDistinctAssets(request.assets());
    validateTotalWeight(request.assets());
    if (previous == null) {
      var result =
          response(request, portfolioAnalysisAiClient.analyze(createTargets(request.assets())));
      return summarize(result);
    }
    try {
      // 날짜는 명시적인 ISO 문자열로 보내고 포트폴리오 소유자 정보는 보내지 않는다.
      String context =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .writeValueAsString(
                  previous.impacts().stream()
                      .map(
                          impact ->
                              Map.of(
                                  "assetName", impact.assetName(),
                                  "direction",
                                      impact.direction() == null
                                          ? "INSUFFICIENT"
                                          : impact.direction().name(),
                                  "issueSummary", impact.issueSummary(),
                                  "sources",
                                      impact.sources().stream()
                                          .map(
                                              source ->
                                                  Map.of(
                                                      "url",
                                                      source.url(),
                                                      "occurredOn",
                                                      source.occurredOn() == null
                                                          ? ""
                                                          : source.occurredOn().toString(),
                                                      "excerpt",
                                                      source.excerpt(),
                                                      "factQuote",
                                                      source.factQuote() == null
                                                          ? ""
                                                          : source.factQuote()))
                                          .toList()))
                      .toList());
      var candidate =
          response(
              request, portfolioAnalysisAiClient.refresh(createTargets(request.assets()), context));
      var revision = PortfolioAnalysisRefresh.compare(previous, candidate);
      if (revision.outcome()
          != org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck.Outcome.UPDATED) {
        return candidate;
      }
      var merged = revision.analysis();
      return summarize(merged);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("이전 분석 근거를 변환하지 못했습니다.");
    }
  }

  protected PortfolioAnalysisResponse response(
      PortfolioAnalysisRequest request, PortfolioAnalysisResult result) {
    Map<String, BigDecimal> weightByAssetName = createWeightByAssetName(request.assets());

    List<PortfolioAssetImpactResponse> impacts =
        result.impacts().stream()
            .map(
                impact ->
                    PortfolioAssetImpactResponse.of(
                        impact, weightByAssetName.get(impact.assetName())))
            .toList();
    List<PortfolioAnalysisSourceResponse> sources =
        result.sources().stream().map(PortfolioAnalysisSourceResponse::from).toList();
    PortfolioImpactSummary impactSummary =
        PortfolioImpactSummary.calculate(
            impacts.stream()
                .map(
                    impact ->
                        new PortfolioImpactSummary.AssetImpact(
                            impact.weight(), impact.direction(), impact.impactLevel()))
                .toList());
    return PortfolioAnalysisResponse.of(
        request.assets().size(),
        impactSummary,
        Instant.now(),
        result.overallImpact(),
        impacts,
        sources);
  }

  private void validateDistinctAssets(List<PortfolioAssetRequest> portfolioAssets) {
    Set<String> assetNames = new HashSet<>();
    boolean hasDuplicate =
        portfolioAssets.stream()
            .map(PortfolioAssetRequest::assetName)
            .map(assetName -> assetName.toLowerCase(Locale.ROOT))
            .anyMatch(assetName -> !assetNames.add(assetName));

    if (hasDuplicate) {
      throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    }
  }

  private void validateTotalWeight(List<PortfolioAssetRequest> portfolioAssets) {
    if (portfolioAssets.isEmpty()) {
      return;
    }

    BigDecimal totalWeight =
        portfolioAssets.stream()
            .map(PortfolioAssetRequest::weight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_WEIGHT_TOTAL);
    }
  }

  private Map<String, BigDecimal> createWeightByAssetName(
      List<PortfolioAssetRequest> portfolioAssets) {
    return portfolioAssets.stream()
        .collect(Collectors.toMap(PortfolioAssetRequest::assetName, PortfolioAssetRequest::weight));
  }

  protected List<PortfolioAnalysisTarget> createTargets(
      List<PortfolioAssetRequest> portfolioAssets) {
    return portfolioAssets.stream()
        .map(portfolioAsset -> new PortfolioAnalysisTarget(portfolioAsset.assetName()))
        .toList();
  }
}
