package org.grit.daynomy.portfolio.ai;

import java.util.List;

public interface PortfolioAnalysisAiClient {
  PortfolioAnalysisResult analyze(List<PortfolioAnalysisTarget> targets);

  PortfolioAnalysisResult refresh(List<PortfolioAnalysisTarget> targets, String previousEvidence);
}
