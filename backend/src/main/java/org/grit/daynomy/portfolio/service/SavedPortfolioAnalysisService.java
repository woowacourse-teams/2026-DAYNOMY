package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPortfolioAnalysisService {

  private final SavedPortfolioAnalysisTransactionService transactions;
  private final PortfolioAnalysisService analysisService;

  public java.util.List<org.grit.daynomy.portfolio.dto.PortfolioAnalysisHistoryResponse> history(
      Long memberId) {
    return transactions.history(memberId);
  }

  public SavedPortfolioAnalysisResponse get(Long memberId) {
    return transactions.get(memberId);
  }

  public SavedPortfolioAnalysisResponse analyze(Long memberId, PortfolioAnalysisRequest request) {
    return execute(memberId, transactions.prepare(memberId, request), false);
  }

  public SavedPortfolioAnalysisResponse refresh(Long memberId) {
    return execute(memberId, transactions.prepareRefresh(memberId), true);
  }

  /** 수동 작업과 같은 잠금·검증·저장을 사용하며 이미 진행 중이면 추가 호출하지 않는다. */
  public SavedPortfolioAnalysisResponse refreshAutomatically(
      Long memberId, java.util.Set<String> names) {
    if (names.isEmpty()) return transactions.get(memberId);
    var prepared = transactions.prepareAutomatic(memberId, names);
    if (prepared.saved() != null) return prepared.saved();
    try {
      var revision =
          analysisService.refreshAssets(
              new PortfolioAnalysisRequest(prepared.assets()), prepared.previous(), names);
      var core =
          transactions.completeAutomaticCore(
              memberId,
              prepared.token(),
              prepared.assets(),
              revision.analysis().withOverallImpact(""),
              names);
      return "PENDING".equals(core.summaryStatus()) ? summarize(memberId, prepared, core) : core;
    } catch (RuntimeException exception) {
      fail(memberId, prepared, exception);
      throw exception;
    }
  }

  SavedPortfolioAnalysisResponse executeStaged(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      boolean refresh) {
    var core = completeCore(memberId, prepared, refresh);
    if (!"PENDING".equals(core.summaryStatus())) {
      return core;
    }
    return summarize(memberId, prepared, core);
  }

  private SavedPortfolioAnalysisResponse completeCore(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      boolean refresh) {
    long started = System.nanoTime();
    try {
      var request = new PortfolioAnalysisRequest(prepared.assets());
      var result =
          !refresh && prepared.previous() == null
              ? analysisService.analyzeAssets(request)
              : analysisService.refreshAssets(request, prepared.previous()).analysis();
      var core =
          transactions.completeCore(
              memberId, prepared.token(), prepared.assets(), result.withOverallImpact(""));
      log.atInfo()
          .addKeyValue("event", "portfolio.analysis.core.completed")
          .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
          .addKeyValue("analyzedCount", core.analysis().analyzedAssetCount())
          .log("포트폴리오 핵심 결과 저장 완료");
      return core;
    } catch (RuntimeException exception) {
      fail(memberId, prepared, exception);
      throw exception;
    }
  }

  private SavedPortfolioAnalysisResponse summarize(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      SavedPortfolioAnalysisResponse core) {
    try {
      var result = analysisService.summarize(core.analysis());
      return transactions.completeSummary(
          memberId, prepared.token(), core.analysisId(), result.overallImpact());
    } catch (RuntimeException exception) {
      // 종합 설명 실패로 이미 검증·저장한 점수와 근거를 실패 처리하지 않는다.
      transactions.failSummary(memberId, prepared.token(), core.analysisId());
      return transactions.get(memberId);
    }
  }

  private SavedPortfolioAnalysisResponse execute(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      boolean refresh) {
    if (prepared.saved() != null) {
      return prepared.saved();
    }
    try {
      // 외부 API 대기 중에는 DB 트랜잭션과 행 잠금을 유지하지 않는다.
      var result =
          !refresh && prepared.previous() == null
              ? analysisService.analyze(new PortfolioAnalysisRequest(prepared.assets()), true)
              : analysisService.refresh(
                  new PortfolioAnalysisRequest(prepared.assets()), prepared.previous());
      return transactions.complete(memberId, prepared.token(), prepared.assets(), result);
    } catch (RuntimeException exception) {
      fail(memberId, prepared, exception);
      throw exception;
    }
  }

  private void fail(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      RuntimeException exception) {
    String code =
        exception instanceof BusinessException business
            ? business.errorCode().code()
            : "PORTFOLIO_ANALYSIS_FAILED";
    transactions.fail(memberId, prepared.token(), code);
  }
}
