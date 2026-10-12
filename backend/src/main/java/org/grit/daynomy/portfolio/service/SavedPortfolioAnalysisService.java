package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavedPortfolioAnalysisService {

  private final SavedPortfolioAnalysisTransactionService transactions;
  private final PortfolioAnalysisService analysisService;

  public SavedPortfolioAnalysisResponse get(Long memberId) {
    return transactions.get(memberId);
  }

  public SavedPortfolioAnalysisResponse analyze(Long memberId, PortfolioAnalysisRequest request) {
    var prepared = transactions.prepare(memberId, request);
    if (prepared.saved() != null) {
      return prepared.saved();
    }
    try {
      // 외부 API 대기 중에는 DB 트랜잭션과 행 잠금을 유지하지 않는다.
      var result = analysisService.analyze(new PortfolioAnalysisRequest(prepared.assets()), true);
      return transactions.complete(memberId, prepared.token(), prepared.assets(), result);
    } catch (RuntimeException exception) {
      String code =
          exception instanceof BusinessException business
              ? business.errorCode().code()
              : "PORTFOLIO_ANALYSIS_FAILED";
      transactions.fail(memberId, prepared.token(), code);
      throw exception;
    }
  }
}
