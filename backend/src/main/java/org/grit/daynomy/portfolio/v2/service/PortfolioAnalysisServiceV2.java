package org.grit.daynomy.portfolio.v2.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisNarrative;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTrial;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.PortfolioAssetAnalysisService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortfolioAnalysisServiceV2 extends PortfolioAnalysisService {
  private final PortfolioAssetAnalysisService assetAnalysis;

  public PortfolioAnalysisServiceV2(
      PortfolioAnalysisAiClient ai,
      PortfolioAssetAnalysisService assetAnalysis,
      PortfolioAnalysisNarrative narrative,
      GuestPortfolioAnalysisTrial guestTrial) {
    super(ai, assetAnalysis, narrative, guestTrial);
    this.assetAnalysis = assetAnalysis;
  }

  @Override
  protected List<PortfolioAnalysisTarget> createTargets(List<PortfolioAssetRequest> assets) {
    long started = System.nanoTime();
    var levels =
        assets.size() <= PortfolioAnalysisSelection.LIMIT
            ? Map.<String, ImpactLevel>of()
            : assetAnalysis.cachedImpactLevels(assets.stream().map(a -> a.assetName()).toList());
    var selected = PortfolioAnalysisSelection.select(assets, levels);
    log.atInfo()
        .addKeyValue("event", "portfolio.analysis.v2.selected")
        .addKeyValue("totalAssetCount", assets.size())
        .addKeyValue("selectedAssetCount", selected.size())
        .addKeyValue(
            "cachedHighImpactCount",
            selected.stream().filter(a -> levels.get(a.assetName()) == ImpactLevel.HIGH).count())
        .addKeyValue("selectionMs", (System.nanoTime() - started) / 1_000_000)
        .log("V2 중점 분석 대상 선별 완료");
    return selected.stream().map(asset -> new PortfolioAnalysisTarget(asset.assetName())).toList();
  }

  @Override
  protected PortfolioAnalysisResponse response(
      PortfolioAnalysisRequest request, PortfolioAnalysisResult result) {
    return super.response(request, result).withExcludedAssets(request.assets());
  }
}
