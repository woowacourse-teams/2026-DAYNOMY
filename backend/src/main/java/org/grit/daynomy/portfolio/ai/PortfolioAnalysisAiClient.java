package org.grit.daynomy.portfolio.ai;

import java.util.List;

public interface PortfolioAnalysisAiClient {
  PortfolioAnalysisResult analyze(List<PortfolioAnalysisTarget> targets);
}
