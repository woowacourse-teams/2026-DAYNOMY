package org.grit.daynomy.portfolio.service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.grit.daynomy.portfolio.domain.GuestPortfolioAnalysisTrialState;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.dto.GuestPortfolioAnalysisTaskResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@SessionScope
@Component
public class GuestPortfolioAnalysisTrial {

  private final Task task = new Task();

  public <T> T use(Supplier<T> analysis) {
    return task.use(analysis);
  }

  public Prepared reserve(PortfolioAnalysisRequest request) {
    return task.reserve(request);
  }

  public Prepared retry(UUID id) {
    return task.retry(id);
  }

  public GuestPortfolioAnalysisTaskResponse get() {
    return task.response();
  }

  public GuestPortfolioAnalysisTaskResponse get(UUID id) {
    return task.response(id);
  }

  public record Prepared(Task task, UUID token) {}

  /** 세션 프록시 대신 실제 작업 객체를 실행기에 전달해 HTTP 요청 종료 후에도 사용할 수 있다. */
  public static class Task {
    private final GuestPortfolioAnalysisTrialState state = new GuestPortfolioAnalysisTrialState();
    private PortfolioAnalysisRequest request;
    private PortfolioAnalysisResponse result;

    private <T> T use(Supplier<T> analysis) {
      synchronized (this) {
        state.reserveLegacy();
      }
      try {
        return analysis.get();
      } catch (RuntimeException | Error exception) {
        synchronized (this) {
          state.releaseLegacy();
        }
        throw exception;
      }
    }

    private synchronized Prepared reserve(PortfolioAnalysisRequest input) {
      var token =
          state.reserve(
              input.assets().stream()
                  .map(a -> new PortfolioAnalysisSnapshot.Asset(a.assetName(), a.weight()))
                  .toList(),
              Instant.now());
      if (token != null) {
        request = input;
      }
      return new Prepared(this, token);
    }

    private synchronized Prepared retry(UUID id) {
      return new Prepared(this, state.retry(id, Instant.now()));
    }

    public synchronized PortfolioAnalysisRequest request() {
      return request;
    }

    public synchronized GuestPortfolioAnalysisTaskResponse response() {
      state.expire(Instant.now());
      return new GuestPortfolioAnalysisTaskResponse(
          state.getTaskId(),
          state.getStatus().name(),
          result,
          state.getSummaryStatus() == null ? null : state.getSummaryStatus().name(),
          state.getFailureCode(),
          state.retryAllowed());
    }

    private synchronized GuestPortfolioAnalysisTaskResponse response(UUID id) {
      state.requireTask(id);
      return response();
    }

    public synchronized boolean publishCore(UUID token, PortfolioAnalysisResponse analysis) {
      if (!state.publishCore(token, Instant.now())) {
        return false;
      }
      result = analysis;
      return true;
    }

    public synchronized void complete(UUID token, PortfolioAnalysisResponse analysis) {
      if (state.complete(token, Instant.now())) {
        result = analysis;
      }
    }

    public synchronized void fail(UUID token, String code) {
      state.fail(token, code, Instant.now());
    }

    public synchronized void reject(UUID token) {
      state.reject(token, Instant.now());
    }
  }
}
