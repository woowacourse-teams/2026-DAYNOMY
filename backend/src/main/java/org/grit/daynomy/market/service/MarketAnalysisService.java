package org.grit.daynomy.market.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.dto.MarketAnalysisResponse;
import org.grit.daynomy.market.exception.MarketErrorCode;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.News;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MarketAnalysisService {

  private final NewsMarketAnalysisRepository newsMarketAnalysisRepository;

  @Transactional
  public void saveMarketAnalysis(News news, NewsMarketAnalysis marketAnalysis) {
    newsMarketAnalysisRepository.save(
        new NewsMarketAnalysis(
            news,
            marketAnalysis.getCause(),
            marketAnalysis.getAssets(),
            marketAnalysis.getScenarios()));
  }

  public MarketAnalysisResponse getMarketAnalysis(Long newsId) {
    return newsMarketAnalysisRepository
        .findByNewsId(newsId)
        .map(MarketAnalysisResponse::from)
        .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_ANALYSIS_NOT_FOUND));
  }
}
