package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** 기존 분석·보정 시나리오에서는 위험 검색 미발견을 응답한다. 위험 검색 자체는 별도 테스트한다. */
class PortfolioRiskSearchStub extends Dispatcher {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final java.util.Queue<MockResponse> queue =
      new java.util.concurrent.ConcurrentLinkedQueue<>();
  private final AtomicInteger calls = new AtomicInteger();

  @Override
  public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
    var risk = respondIfRisk(request);
    if (risk != null) return risk;
    var response = queue.poll();
    return response == null ? new MockResponse().setResponseCode(404) : response;
  }

  void enqueueResponse(MockResponse response) {
    queue.add(response);
  }

  MockResponse respondIfRisk(RecordedRequest request) {
    if (!isRisk(request)) return null;
    calls.incrementAndGet();
    try {
      var body = MAPPER.readTree(request.getBody().clone().readUtf8());
      var assets =
          MAPPER.readTree(body.path("input").get(1).path("content").asText()).path("assets");
      var output = MAPPER.createObjectNode();
      var selected = output.putArray("assets");
      assets.forEach(
          asset -> {
            var item = selected.addObject().put("assetName", asset.path("assetName").asText());
            item.putArray("urls");
            item.putNull("alternativeUrl");
          });
      return new MockResponse()
          .setHeader("Content-Type", "application/json")
          .setBody(
              MAPPER.writeValueAsString(
                  Map.of(
                      "output",
                      List.of(
                          Map.of("type", "web_search_call", "status", "completed"),
                          Map.of(
                              "content",
                              List.of(
                                  Map.of(
                                      "type",
                                      "output_text",
                                      "text",
                                      output.toString(),
                                      "annotations",
                                      List.of())))))));
    } catch (Exception failure) {
      throw new AssertionError("위험 검색 fixture를 생성하지 못했습니다.", failure);
    }
  }

  int nonRiskRequestCount(MockWebServer server) {
    return server.getRequestCount() - calls.get();
  }

  static RecordedRequest takeNonRiskRequest(MockWebServer server) throws InterruptedException {
    RecordedRequest request;
    do {
      request = server.takeRequest(100, TimeUnit.MILLISECONDS);
    } while (request != null && isRisk(request));
    return request;
  }

  private static boolean isRisk(RecordedRequest request) {
    try {
      return MAPPER
          .readTree(request.getBody().clone().readUtf8())
          .path("text")
          .path("format")
          .path("name")
          .asText()
          .equals("portfolio_risk_sources");
    } catch (Exception failure) {
      throw new AssertionError("검색 요청 fixture를 읽지 못했습니다.", failure);
    }
  }
}
