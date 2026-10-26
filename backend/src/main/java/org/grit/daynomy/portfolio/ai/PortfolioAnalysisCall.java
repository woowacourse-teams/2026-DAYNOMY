package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/** 포트폴리오 분석의 실제 HTTP 호출마다 완료 로그를 한 번 남긴다. */
@Slf4j
public final class PortfolioAnalysisCall {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ThreadLocal<Observer> OBSERVER = new ThreadLocal<>();

  private PortfolioAnalysisCall() {}

  // 단일 종목의 동기 호출 범위에서만 사용한다. 수동 분석에는 관찰자를 등록하지 않는다.
  public static <T> T observe(Observer observer, Supplier<T> operation) {
    Observer previous = OBSERVER.get();
    OBSERVER.set(observer);
    try {
      return operation.get();
    } finally {
      if (previous == null) OBSERVER.remove();
      else OBSERVER.set(previous);
    }
  }

  public static Map<String, Object> prepare(Map<String, Object> request) {
    Observer observer = OBSERVER.get();
    return observer == null ? request : observer.prepare(request);
  }

  public interface Observer {
    Map<String, Object> prepare(Map<String, Object> request);

    void before(String phase, String model);

    void after(String response, RuntimeException failure);
  }

  public static String execute(
      RestClient.RequestHeadersSpec<?> request, String phase, String model) {
    Observer observer = OBSERVER.get();
    if (observer != null) observer.before(phase, model);
    long started = System.nanoTime();
    String response = null;
    RuntimeException failure = null;
    try {
      response = request.retrieve().body(String.class);
      return response;
    } catch (RuntimeException exception) {
      failure = exception;
      throw exception;
    } finally {
      var event =
          log.atInfo()
              .addKeyValue("event", "portfolio.analysis.ai.call.completed")
              .addKeyValue("phase", phase)
              .addKeyValue("model", model)
              .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
              .addKeyValue("outcome", failure == null ? "SUCCESS" : "FAILED");
      if (failure != null) {
        event.addKeyValue("errorType", failure.getClass().getSimpleName());
        if (failure instanceof HttpStatusCodeException http) {
          event.addKeyValue("httpStatus", http.getStatusCode().value());
        }
      }
      addUsage(event, response);
      event.log("포트폴리오 AI 호출 종료");
      if (observer != null) observer.after(response, failure);
    }
  }

  private static void addUsage(LoggingEventBuilder event, String response) {
    if (response == null) return;
    try {
      JsonNode root = MAPPER.readTree(response);
      if (root == null) return;
      String responseId = root.path("id").asText("");
      if (responseId.matches("resp_[A-Za-z0-9_-]{1,200}")) {
        event.addKeyValue("responseId", responseId);
      }
      addTokenCount(event, "inputTokens", root.path("usage").path("input_tokens"));
      addTokenCount(event, "outputTokens", root.path("usage").path("output_tokens"));
    } catch (JsonProcessingException exception) {
      // 사용량을 읽지 못해도 기존 응답 검증과 예외 처리는 바꾸지 않는다.
    }
  }

  private static void addTokenCount(LoggingEventBuilder event, String key, JsonNode value) {
    if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0) {
      event.addKeyValue(key, value.longValue());
    }
  }
}
