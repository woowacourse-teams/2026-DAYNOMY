package org.grit.daynomy.market.ai;

import org.grit.daynomy.market.domain.analysis.MarketAnalysis;

public interface MarketAnalysisAiClient {

  MarketAnalysis analyze(String newsContent);
}
