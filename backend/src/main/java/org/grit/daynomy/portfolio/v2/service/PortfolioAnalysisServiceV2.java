package org.grit.daynomy.portfolio.v2.service;

import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisNarrative;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTrial;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.PortfolioAssetAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAnalysisServiceV2 extends PortfolioAnalysisService {
  public PortfolioAnalysisServiceV2(
      PortfolioAnalysisAiClient ai,
      PortfolioAssetAnalysisService assetAnalysis,
      PortfolioAnalysisNarrative narrative,
      GuestPortfolioAnalysisTrial guestTrial) {
    super(ai, assetAnalysis, narrative, guestTrial);
  }
}
