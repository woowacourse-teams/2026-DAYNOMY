package org.grit.daynomy.portfolio.service;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SavedPortfolioAnalysisTaskService {

  private final SavedPortfolioAnalysisTransactionService transactions;
  private final SavedPortfolioAnalysisService analysis;
  private final TaskExecutor executor;

  public SavedPortfolioAnalysisTaskService(
      SavedPortfolioAnalysisTransactionService transactions,
      SavedPortfolioAnalysisService analysis,
      @Qualifier("portfolioAnalysisTaskExecutor") TaskExecutor executor) {
    this.transactions = transactions;
    this.analysis = analysis;
    this.executor = executor;
  }

  public SavedPortfolioAnalysisResponse start(Long memberId, boolean refresh) {
    var prepared = transactions.prepareTask(memberId, refresh);
    if (prepared.saved() != null) {
      return prepared.saved();
    }
    try {
      var processing = transactions.get(memberId);
      executor.execute(() -> execute(memberId, prepared, refresh));
      return processing;
    } catch (RejectedExecutionException exception) {
      transactions.fail(
          memberId, prepared.token(), PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY.code());
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY);
    } catch (RuntimeException exception) {
      transactions.fail(memberId, prepared.token(), "PORTFOLIO_ANALYSIS_FAILED");
      throw exception;
    }
  }

  private void execute(
      Long memberId,
      SavedPortfolioAnalysisTransactionService.PreparedAnalysis prepared,
      boolean refresh) {
    long started = System.nanoTime();
    try {
      var result = analysis.executeStaged(memberId, prepared, refresh);
      log.atInfo()
          .addKeyValue("event", "portfolio.analysis.task.completed")
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .addKeyValue(
              "outcome", "FAILED".equals(result.summaryStatus()) ? "SUMMARY_FAILED" : "SUCCESS")
          .log("포트폴리오 백그라운드 분석 종료");
    } catch (RuntimeException exception) {
      // 실패 상태는 기존 분석 서비스에서 저장한다. 응답이 끝난 HTTP 요청에 예외를 전달하지 않는다.
      log.atWarn()
          .addKeyValue("event", "portfolio.analysis.task.completed")
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .addKeyValue("outcome", "FAILED")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log("포트폴리오 백그라운드 분석 실패");
    }
  }
}
