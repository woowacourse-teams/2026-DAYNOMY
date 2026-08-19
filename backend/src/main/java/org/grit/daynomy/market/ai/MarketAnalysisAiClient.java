package org.grit.daynomy.market.ai;

import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;

public interface MarketAnalysisAiClient {

  NewsMarketAnalysis analyze(String newsContent);
}
