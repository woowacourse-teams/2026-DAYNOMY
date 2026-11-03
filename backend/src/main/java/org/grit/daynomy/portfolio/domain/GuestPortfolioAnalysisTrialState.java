package org.grit.daynomy.portfolio.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;

/** 세션당 무료 체험 예약과 동일 작업의 명시적 재시도만 허용한다. */
@Getter
public class GuestPortfolioAnalysisTrialState {

  private static final Duration TIMEOUT = Duration.ofMinutes(5);
  private static final int MAX_ATTEMPTS = 2;

  public enum Status {
    EMPTY,
    PROCESSING,
    READY,
    FAILED
  }

  private boolean consumed;
  private UUID taskId;
  private UUID token;
  private Instant expiresAt;
  private int attempts;
  private List<PortfolioAnalysisSnapshot.Asset> assets = List.of();
  private Status status = Status.EMPTY;
  private PortfolioAnalysis.SummaryStatus summaryStatus;
  private String failureCode;
  private boolean coreAvailable;

  public void reserveLegacy() {
    if (consumed) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED);
    }
    consumed = true;
  }

  public void releaseLegacy() {
    if (taskId == null) {
      consumed = false;
    }
  }

  public UUID reserve(List<PortfolioAnalysisSnapshot.Asset> requested, Instant now) {
    expire(now);
    if (taskId != null && PortfolioAnalysisSnapshot.sameAssets(assets, requested)) {
      return null;
    }
    if (consumed) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED);
    }
    consumed = true;
    taskId = UUID.randomUUID();
    assets = List.copyOf(requested);
    return begin(now);
  }

  public UUID retry(UUID id, Instant now) {
    requireTask(id);
    expire(now);
    if (status == Status.PROCESSING) {
      return null;
    }
    if (!retryAllowed()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED);
    }
    return begin(now);
  }

  public void requireTask(UUID id) {
    if (taskId == null || !taskId.equals(id)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TASK_NOT_FOUND);
    }
  }

  public boolean retryAllowed() {
    return status == Status.FAILED
        && attempts < MAX_ATTEMPTS
        && !PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED.code().equals(failureCode);
  }

  public boolean publishCore(UUID expected, Instant now) {
    if (!owns(expected, now) || coreAvailable) {
      return false;
    }
    coreAvailable = true;
    summaryStatus = PortfolioAnalysis.SummaryStatus.PENDING;
    return true;
  }

  public boolean complete(UUID expected, Instant now) {
    if (!owns(expected, now) || !coreAvailable) {
      return false;
    }
    status = Status.READY;
    summaryStatus = PortfolioAnalysis.SummaryStatus.READY;
    token = null;
    return true;
  }

  public void fail(UUID expected, String code, Instant now) {
    if (!owns(expected, now)) {
      return;
    }
    if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,99}")) {
      throw new IllegalArgumentException("실패 원인은 예외 메시지가 아닌 오류 코드여야 합니다.");
    }
    status = coreAvailable ? Status.READY : Status.FAILED;
    summaryStatus = coreAvailable ? PortfolioAnalysis.SummaryStatus.FAILED : null;
    failureCode = code;
    token = null;
  }

  public void reject(UUID expected, Instant now) {
    if (!owns(expected, now)) {
      return;
    }
    attempts--;
    fail(expected, PortfolioErrorCode.PORTFOLIO_ANALYSIS_BUSY.code(), now);
  }

  public void expire(Instant now) {
    if (status == Status.PROCESSING && !now.isBefore(expiresAt)) {
      status = coreAvailable ? Status.READY : Status.FAILED;
      summaryStatus = coreAvailable ? PortfolioAnalysis.SummaryStatus.FAILED : null;
      failureCode = PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED.code();
      token = null;
    }
  }

  private UUID begin(Instant now) {
    attempts++;
    token = UUID.randomUUID();
    expiresAt = now.plus(TIMEOUT);
    status = Status.PROCESSING;
    summaryStatus = null;
    failureCode = null;
    coreAvailable = false;
    return token;
  }

  private boolean owns(UUID expected, Instant now) {
    expire(now);
    return status == Status.PROCESSING && expected != null && expected.equals(token);
  }
}
