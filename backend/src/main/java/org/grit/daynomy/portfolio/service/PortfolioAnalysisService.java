package org.grit.daynomy.portfolio.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.grit.daynomy.bookmark.repository.BookmarkRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PortfolioAnalysisService {

  private final NewsRepository newsRepository;
  private final BookmarkRepository bookmarkRepository;
  private final AssetRepository assetRepository;
  private final PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  public PortfolioAnalysisResponse getPortfolioAnalysis(Long memberId, Long newsId) {
    News news =
        newsRepository
            .findById(newsId)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    List<Bookmark> bookmarks = bookmarkRepository.findAllByMemberIdOrderByIdAsc(memberId);

    if (bookmarks.isEmpty()) {
      return new PortfolioAnalysisResponse(List.of());
    }

    Map<Long, Asset> assetById = findAssetsById(bookmarks);
    List<PortfolioAnalysisTarget> targets = createTargets(bookmarks, assetById);
    PortfolioAnalysisResult result = portfolioAnalysisAiClient.analyze(news.getContent(), targets);

    List<PortfolioAssetImpactResponse> impacts =
        result.impacts().stream()
            .map(
                impact ->
                    PortfolioAssetImpactResponse.of(impact, getAsset(assetById, impact.assetId())))
            .toList();
    return new PortfolioAnalysisResponse(impacts);
  }

  private Map<Long, Asset> findAssetsById(List<Bookmark> bookmarks) {
    List<Long> assetIds = bookmarks.stream().map(bookmark -> bookmark.getAsset().getId()).toList();
    return assetRepository.findAllById(assetIds).stream()
        .collect(Collectors.toMap(Asset::getId, Function.identity()));
  }

  private List<PortfolioAnalysisTarget> createTargets(
      List<Bookmark> bookmarks, Map<Long, Asset> assetById) {
    return bookmarks.stream()
        .map(
            bookmark -> {
              Asset asset = getAsset(assetById, bookmark.getAsset().getId());
              return new PortfolioAnalysisTarget(
                  asset.getId(),
                  bookmark.getId(),
                  asset.getName(),
                  asset.getCategory().name(),
                  asset.getAssetCode());
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
