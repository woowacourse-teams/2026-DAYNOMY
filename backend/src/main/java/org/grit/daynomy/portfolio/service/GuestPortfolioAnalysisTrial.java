package org.grit.daynomy.portfolio.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.grit.daynomy.portfolio.domain.GuestPortfolioAnalysisTrialState;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.dto.GuestPortfolioAnalysisTaskResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.repository.GuestPortfolioAnalysisTrialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import tools.jackson.databind.json.JsonMapper;

@RequestScope
@Component
public class GuestPortfolioAnalysisTrial {

  private final Task task;

  @Autowired
  public GuestPortfolioAnalysisTrial(
      HttpServletRequest request,
      GuestPortfolioAnalysisTrialRepository repository,
      JsonMapper mapper) {
    task = new Task(request.getSession().getId(), repository, mapper);
  }

  /** Domain-only callers can own an in-memory trial without an HTTP or database context. */
  public GuestPortfolioAnalysisTrial() {
    task = new Task();
  }

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

  /** A worker retains a session identifier, not the request-scoped proxy or a stale state copy. */
  public static class Task {
    private final String sessionId;
    private final GuestPortfolioAnalysisTrialRepository repository;
    private final JsonMapper mapper;
    private final Data local;

    private Task() {
      sessionId = null;
      repository = null;
      mapper = null;
      local = new Data();
    }

    private Task(
        String sessionId, GuestPortfolioAnalysisTrialRepository repository, JsonMapper mapper) {
      this.sessionId = sessionId;
      this.repository = repository;
      this.mapper = mapper;
      local = null;
    }

    private synchronized <T> T change(Function<Data, T> transition) {
      if (repository == null) {
        return transition.apply(local);
      }
      return repository.update(
          sessionId,
          json -> {
            Data data =
                json == null || "null".equals(json)
                    ? new Data()
                    : new Data(mapper.readValue(json, Document.class));
            T result = transition.apply(data);
            return new GuestPortfolioAnalysisTrialRepository.Update<>(
                mapper.writeValueAsString(data.document()), result);
          });
    }

    private <T> T use(Supplier<T> analysis) {
      change(
          data -> {
            data.state.reserveLegacy();
            return null;
          });
      try {
        return analysis.get();
      } catch (RuntimeException | Error exception) {
        change(
            data -> {
              data.state.releaseLegacy();
              return null;
            });
        throw exception;
      }
    }

    private Prepared reserve(PortfolioAnalysisRequest input) {
      UUID token =
          change(
              data -> {
                var reserved =
                    data.state.reserve(
                        input.assets().stream()
                            .map(
                                a -> new PortfolioAnalysisSnapshot.Asset(a.assetName(), a.weight()))
                            .toList(),
                        Instant.now());
                if (reserved != null) {
                  data.request = input;
                }
                return reserved;
              });
      return new Prepared(this, token);
    }

    private Prepared retry(UUID id) {
      return new Prepared(this, change(data -> data.state.retry(id, Instant.now())));
    }

    public PortfolioAnalysisRequest request() {
      return change(data -> data.request);
    }

    public GuestPortfolioAnalysisTaskResponse response() {
      return change(Data::response);
    }

    private GuestPortfolioAnalysisTaskResponse response(UUID id) {
      return change(
          data -> {
            data.state.requireTask(id);
            return data.response();
          });
    }

    public boolean publishCore(UUID token, PortfolioAnalysisResponse analysis) {
      return change(
          data -> {
            if (!data.state.publishCore(token, Instant.now())) {
              return false;
            }
            data.result = analysis;
            return true;
          });
    }

    public void complete(UUID token, PortfolioAnalysisResponse analysis) {
      change(
          data -> {
            if (data.state.complete(token, Instant.now())) {
              data.result = analysis;
            }
            return null;
          });
    }

    public void fail(UUID token, String code) {
      change(
          data -> {
            data.state.fail(token, code, Instant.now());
            return null;
          });
    }

    public void reject(UUID token) {
      change(
          data -> {
            data.state.reject(token, Instant.now());
            return null;
          });
    }
  }

  public record Document(
      GuestPortfolioAnalysisTrialState.Snapshot state,
      PortfolioAnalysisRequest request,
      PortfolioAnalysisResponse result) {}

  private static class Data {
    private final GuestPortfolioAnalysisTrialState state;
    private PortfolioAnalysisRequest request;
    private PortfolioAnalysisResponse result;

    private Data() {
      state = new GuestPortfolioAnalysisTrialState();
    }

    private Data(Document document) {
      state = GuestPortfolioAnalysisTrialState.restore(document.state());
      request = document.request();
      result = document.result();
    }

    private Document document() {
      return new Document(state.snapshot(), request, result);
    }

    private GuestPortfolioAnalysisTaskResponse response() {
      state.expire(Instant.now());
      return new GuestPortfolioAnalysisTaskResponse(
          state.getTaskId(),
          state.getStatus().name(),
          result,
          state.getSummaryStatus() == null ? null : state.getSummaryStatus().name(),
          state.getFailureCode(),
          state.retryAllowed());
    }
  }
}
