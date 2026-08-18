package org.grit.daynomy.market.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.market.dto.MarketAnalysisResponse;
import org.grit.daynomy.market.parser.analysis.MarketAnalysisParser;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MarketAnalysisService {

  private final NewsMarketAnalysisRepository newsMarketAnalysisRepository;

  public MarketAnalysisResponse getMarketAnalysis(Long newsId) {
    return newsMarketAnalysisRepository
        .findByNewsId(newsId)
        .map(MarketAnalysisParser::toDomain)
        .map(MarketAnalysisResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_ANALYSIS_NOT_FOUND));
  }
}
