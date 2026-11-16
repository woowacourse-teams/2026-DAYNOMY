package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioReviewSimulationService {
  private final PortfolioAnalysisShareService shares;
  private final PortfolioAnalysisService analysis;
  private final PortfolioAnalysisShareRateLimiter rateLimiter;

  public PortfolioAnalysisResponse analyze(
      Long memberId, Long shareId, Long commentId, PortfolioAnalysisRequest request) {
    shares.requireSimulationAccess(memberId, shareId, commentId);
    if (request.assets().isEmpty()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
    }
    rateLimiter.acquireWrite(memberId);
    // 임시 구성에는 종목과 비중만 전달하며 회원의 보유 내역과 저장된 분석은 갱신하지 않는다.
    var snapshot =
        new PortfolioAnalysisRequest(
            request.assets().stream()
                .map(asset -> new PortfolioAssetRequest(asset.assetName(), asset.weight()))
                .toList());
    return analysis.analyze(snapshot, true);
  }
}
