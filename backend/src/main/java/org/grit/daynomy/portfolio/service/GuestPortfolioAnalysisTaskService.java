package org.grit.daynomy.portfolio.service;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.GuestPortfolioAnalysisTaskResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GuestPortfolioAnalysisTaskService {

  private final GuestPortfolioAnalysisTrial trial;
  private final PortfolioAnalysisService analysis;
  private final TaskExecutor executor;

  public GuestPortfolioAnalysisTaskService(
      GuestPortfolioAnalysisTrial trial,
      PortfolioAnalysisService analysis,
      @Qualifier("portfolioAnalysisTaskExecutor") TaskExecutor executor) {
    this.trial = trial;
    this.analysis = analysis;
    this.executor = executor;
  }

  public GuestPortfolioAnalysisTaskResponse get() {
    return trial.get();
  }

  public GuestPortfolioAnalysisTaskResponse get(UUID id) {
    return trial.get(id);
  }

  public GuestPortfolioAnalysisTaskResponse start(PortfolioAnalysisRequest request) {
    analysis.validate(request);
    if (request.assets().isEmpty()) {
      return GuestPortfolioAnalysisTaskResponse.emptyAnalysis();
    }
    return submit(trial.reserve(request));
  }

  public GuestPortfolioAnalysisTaskResponse retry(UUID id) {
    return submit(trial.retry(id));
  }

  private GuestPortfolioAnalysisTaskResponse submit(GuestPortfolioAnalysisTrial.Prepared prepared) {
    if (prepared.token() == null) {
      return prepared.task().response();
    }
    try {
      var processing = prepared.task().response();
      executor.execute(() -> execute(prepared));
      return processing;
    } catch (RejectedExecutionException exception) {
      prepared.task().reject(prepared.token());
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY);
    } catch (RuntimeException exception) {
      prepared.task().fail(prepared.token(), "PORTFOLIO_ANALYSIS_FAILED");
      throw exception;
    }
  }

  private void execute(GuestPortfolioAnalysisTrial.Prepared prepared) {
    long started = System.nanoTime();
    try {
      var core = analysis.analyzeAssets(prepared.task().request());
      if (!prepared.task().publishCore(prepared.token(), core)) {
        return;
      }
      log.atInfo()
          .addKeyValue("event", "portfolio.analysis.core.completed")
          .addKeyValue("audience", "guest")
          .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
          .addKeyValue("analyzedCount", core.analyzedAssetCount())
          .log("비회원 포트폴리오 핵심 결과 준비 완료");
      prepared.task().complete(prepared.token(), analysis.summarize(core));
      log.atInfo()
          .addKeyValue("event", "portfolio.analysis.task.completed")
          .addKeyValue("audience", "guest")
          .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
          .addKeyValue("outcome", "SUCCESS")
          .log("비회원 포트폴리오 백그라운드 분석 종료");
    } catch (RuntimeException exception) {
      String code =
          exception instanceof BusinessException business
              ? business.errorCode().code()
              : "PORTFOLIO_ANALYSIS_FAILED";
      prepared.task().fail(prepared.token(), code);
      log.atWarn()
          .addKeyValue("event", "portfolio.analysis.task.completed")
          .addKeyValue("audience", "guest")
          .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
          .addKeyValue(
              "outcome",
              prepared.task().response().analysis() == null ? "FAILED" : "SUMMARY_FAILED")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log("비회원 포트폴리오 백그라운드 분석 실패");
    }
  }
}
