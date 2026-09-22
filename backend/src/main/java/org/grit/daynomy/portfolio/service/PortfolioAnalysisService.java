package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PortfolioAnalysisService {

  private static final int MAX_ANALYZED_ASSET_COUNT = 3;

  private final NewsRepository newsRepository;
  private final PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  public PortfolioAnalysisResponse analyze(Long newsId, PortfolioAnalysisRequest request) {
    validateDistinctAssets(request.assets());
    validateTotalWeight(request.assets());

    News news =
        newsRepository
            .findByIdAndStatus(newsId, NewsStatus.PUBLISHED)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    if (request.assets().isEmpty()) {
      return PortfolioAnalysisResponse.empty();
    }

    Map<String, BigDecimal> weightByAssetName = createWeightByAssetName(request.assets());
    List<PortfolioAnalysisTarget> targets = createTargets(request.assets());
    PortfolioAnalysisResult result = portfolioAnalysisAiClient.analyze(news.getContent(), targets);

    List<PortfolioAssetImpactResponse> impacts =
        result.impacts().stream()
            .limit(MAX_ANALYZED_ASSET_COUNT)
            .map(
                impact ->
                    PortfolioAssetImpactResponse.of(
                        impact, weightByAssetName.get(impact.assetName())))
            .toList();
    return PortfolioAnalysisResponse.of(request.assets().size(), impacts);
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

  private List<PortfolioAnalysisTarget> createTargets(List<PortfolioAssetRequest> portfolioAssets) {
    return portfolioAssets.stream()
        .map(portfolioAsset -> new PortfolioAnalysisTarget(portfolioAsset.assetName()))
        .toList();
  }
}
