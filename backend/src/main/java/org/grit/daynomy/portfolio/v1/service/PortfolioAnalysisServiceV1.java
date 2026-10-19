package org.grit.daynomy.portfolio.v1.service;

import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisNarrative;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTrial;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.PortfolioAssetAnalysisService;
import org.grit.daynomy.portfolio.v1.ai.OpenAiPortfolioAnalysisClientV1;
import org.grit.daynomy.portfolio.v1.ai.PortfolioAnalysisTargetV1;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAnalysisServiceV1 extends PortfolioAnalysisService {
  private final OpenAiPortfolioAnalysisClientV1 client;
  private final PortfolioAnalysisNarrative narrative;

  public PortfolioAnalysisServiceV1(
      PortfolioAnalysisAiClient ai,
      PortfolioAssetAnalysisService assetAnalysis,
      PortfolioAnalysisNarrative narrative,
      GuestPortfolioAnalysisTrial guestTrial,
      OpenAiPortfolioAnalysisClientV1 client) {
    super(ai, assetAnalysis, narrative, guestTrial);
    this.client = client;
    this.narrative = narrative;
  }

  @Override
  protected PortfolioAnalysisResponse analyze(PortfolioAnalysisRequest request) {
    var targets =
        request.assets().stream()
            .map(asset -> new PortfolioAnalysisTargetV1(asset.assetName(), asset.weight()))
            .toList();
    var result = response(request, client.analyze(targets));
    return result.withOverallImpact(narrative.summarize(result));
  }
}
