package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
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

  private final NewsRepository newsRepository;
  private final AssetRepository assetRepository;
  private final PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  public PortfolioAnalysisResponse analyze(Long newsId, PortfolioAnalysisRequest request) {
    News news =
        newsRepository
            .findByIdAndStatus(newsId, NewsStatus.PUBLISHED)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    if (request.assets().isEmpty()) {
      return PortfolioAnalysisResponse.empty();
    }

    Map<Long, Asset> assetById = findAssetsById(request.assets());
    Map<Long, BigDecimal> weightByAssetId = createWeightByAssetId(request.assets());
    List<PortfolioAnalysisTarget> targets = createTargets(request.assets(), assetById);
    PortfolioAnalysisResult result = portfolioAnalysisAiClient.analyze(news.getContent(), targets);

    List<PortfolioAssetImpactResponse> impacts =
        result.impacts().stream()
            .map(
                impact ->
                    PortfolioAssetImpactResponse.of(
                        impact,
                        getAsset(assetById, impact.assetId()),
                        weightByAssetId.get(impact.assetId())))
            .toList();
    return PortfolioAnalysisResponse.of(request.assets().size(), impacts);
  }

  private Map<Long, Asset> findAssetsById(List<PortfolioAssetRequest> portfolioAssets) {
    List<Long> assetIds = portfolioAssets.stream().map(PortfolioAssetRequest::assetId).toList();
    return assetRepository.findAllById(assetIds).stream()
        .collect(Collectors.toMap(Asset::getId, Function.identity()));
  }

  private Map<Long, BigDecimal> createWeightByAssetId(List<PortfolioAssetRequest> portfolioAssets) {
    return portfolioAssets.stream()
        .collect(Collectors.toMap(PortfolioAssetRequest::assetId, PortfolioAssetRequest::weight));
  }

  private List<PortfolioAnalysisTarget> createTargets(
      List<PortfolioAssetRequest> portfolioAssets, Map<Long, Asset> assetById) {
    return portfolioAssets.stream()
        .map(
            portfolioAsset -> {
              Asset asset = getAsset(assetById, portfolioAsset.assetId());
              return new PortfolioAnalysisTarget(
                  asset.getId(), asset.getName(), asset.getCategory().name(), asset.getAssetCode());
            })
        .toList();
  }

  private Asset getAsset(Map<Long, Asset> assetById, Long assetId) {
    Asset asset = assetById.get(assetId);
    if (asset == null) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ASSET_NOT_FOUND);
    }
    return asset;
  }
}
