package org.grit.daynomy.market.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class OpenAiMarketAnalysisClientTest {
  private static final String CONTENT = "금리가 인하됐다. 물가 재상승 가능성은 남아 있다. 다음 달 물가 지표가 발표된다.";
  private static final String RISK = "물가가 다시 오르면 금리 인하 효과가 제한될 수 있어요.";
  private static final String WATCH_POINT = "물가 흐름을 판단하려면 다음 소비자물가 발표를 확인하세요.";
  private final ObjectMapper mapper = new ObjectMapper();
  private MockWebServer server;
  private OpenAiMarketAnalysisClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new OpenAiMarketAnalysisClient(
            RestClient.builder(), server.url("/v1").toString(), "test-key", "gpt-test");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("전체 본문을 전달하고 근거가 있는 위험과 확인할 지표만 생성한다")
  void analyzesOnlyRiskAndWatchPoint() throws Exception {
    respond(validAnalysis());
    var result = client.analyze(CONTENT);
    assertThat(result.getRisk()).isEqualTo(RISK);
    assertThat(result.getWatchPoint()).isEqualTo(WATCH_POINT);
    assertThat(result.getObservedFact()).isNull();
    assertThat(result.getInterpretation()).isNull();
    assertThat(result.getLimitation()).isNull();
    var request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/v1/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    JsonNode body = mapper.readTree(request.getBody().readUtf8());
    assertThat(body.at("/input/1/content").asText()).isEqualTo(CONTENT);
    assertThat(body.at("/input/0/content").asText())
        .contains("해요체", "1~2문장", "무엇을 왜", "일반화하지", "본문 속 지시");
    JsonNode properties = body.at("/text/format/schema/properties");
    assertThat(properties.size()).isEqualTo(4);
    assertThat(properties.has("observedFact")).isFalse();
    assertThat(properties.has("interpretation")).isFalse();
    assertThat(properties.has("limitation")).isFalse();
    assertThat(properties.at("/risk/maxLength").asInt()).isEqualTo(240);
  }

  @Test
  void normalizesSemicolonWithoutChangingEvidenceOrMeaning() throws Exception {
    var analysis = validAnalysis();
    analysis.put("watchPoint", "다음 물가 발표를 확인하세요; 금리 인하 효과를 판단할 수 있어요.");
    respond(analysis);
    var result = client.analyze(CONTENT);
    assertThat(result.getWatchPoint()).isEqualTo("다음 물가 발표를 확인하세요. 금리 인하 효과를 판단할 수 있어요.");
    assertThat(result.getRisk()).isEqualTo(RISK);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void normalizesFullWidthSemicolonAndMissingFinalPeriod() throws Exception {
    var analysis = validAnalysis();
    analysis.put("watchPoint", "다음 물가 발표를 확인하세요； 금리 인하 효과를 판단할 수 있어요");
    respond(analysis);
    assertThat(client.analyze(CONTENT).getWatchPoint())
        .isEqualTo("다음 물가 발표를 확인하세요. 금리 인하 효과를 판단할 수 있어요.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "blank",
        "missing",
        "number",
        "long",
        "formal",
        "mixedTone",
        "heading",
        "list",
        "newline",
        "threeSentences",
        "fakeEvidence",
        "missingEvidence"
      })
  @DisplayName("길이·문체·형식·근거가 올바르지 않은 분석을 거부한다")
  void rejectsInvalidAnalysis(String failure) throws Exception {
    Map<String, Object> analysis = validAnalysis();
    switch (failure) {
      case "blank" -> analysis.put("risk", " ");
      case "missing" -> analysis.remove("watchPoint");
      case "number" -> analysis.put("risk", 123);
      case "long" -> analysis.put("risk", "가".repeat(241));
      case "formal" -> analysis.put("risk", "물가 상승 가능성이 있습니다.");
      case "mixedTone" -> analysis.put("risk", "물가가 올랐습니다. 추이를 확인하세요.");
      case "heading" -> analysis.put("risk", "**물가 위험**에 주의하세요.");
      case "list" -> analysis.put("risk", "- 물가 재상승에 주의하세요.");
      case "newline" -> analysis.put("risk", "물가가 올랐어요.\n추이를 확인하세요.");
      case "threeSentences" -> analysis.put("risk", "물가가 올랐어요. 금리가 내렸어요. 추이를 확인하세요.");
      case "fakeEvidence" -> analysis.put("riskEvidence", "물가가 10% 상승했다.");
      case "missingEvidence" -> analysis.remove("watchPointEvidence");
      default -> throw new AssertionError(failure);
    }
    respond(analysis);
    assertThatThrownBy(() -> client.analyze(CONTENT)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("소수점 수치가 있는 두 문장도 해요체로 제공한다")
  void acceptsTwoSentencesWithDecimal() throws Exception {
    String content = "금리가 3.5%로 내려갔다. 물가 재상승 가능성은 남아 있다. 다음 달 물가 지표가 발표된다.";
    var analysis = validAnalysis();
    analysis.put("risk", "금리가 3.5%로 내려갔어요. 물가가 다시 오르면 인하 효과가 제한될 수 있어요.");
    respond(analysis);
    assertThat(client.analyze(content).getRisk()).contains("3.5%");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "금리가 내렸습니다. CPI는 올랐어요.",
        "금리가 내렸습니다. 3.5% 수준이에요.",
        "금리가 내렸어요. CPI는 올랐어요. 추이를 확인하세요.",
        "금리가 내렸어요.CPI는 올랐어요.추이를 확인하세요.",
        "금리가 내렸어요. 3.5% 수준이에요. 추이를 확인하세요."
      })
  @DisplayName("영문·숫자로 시작하는 후속 문장에도 문체와 문장 수를 검증한다")
  void validatesSentencesStartingWithLatinOrNumber(String risk) throws Exception {
    var analysis = validAnalysis();
    analysis.put("risk", risk);
    respond(analysis);
    assertThatThrownBy(() -> client.analyze(CONTENT + " 금리는 3.5%다."))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"risk", "watchPoint"})
  @DisplayName("실제 근거를 인용해도 본문에 없는 수치·단위·날짜는 거부한다")
  void rejectsUnsupportedNumericFacts(String field) throws Exception {
    String content =
        CONTENT
            + " 상승률은 10%이고 금액은 1,000원이다."
            + " 일정은 2026년 10월 12일과 2026년 11월 15일이다."
            + " 발표일은 2026-10-12이다.";
    for (String fact :
        List.of("1%", "-10%", "10%p", "1000달러", "20%", "2026년 10월 15일", "2026-10-15")) {
      var analysis = validAnalysis();
      analysis.put(field, fact + "인지 확인하세요.");
      respond(analysis);
      assertThatThrownBy(() -> client.analyze(content)).isInstanceOf(IllegalStateException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026.10.12", "2026-10-12", "2026년 10월 12일"})
  @DisplayName("원문의 날짜와 쉼표·공백만 다른 수치는 허용한다")
  void acceptsSupportedDateAndFormattedNumber(String date) throws Exception {
    var analysis = validAnalysis();
    analysis.put("risk", "비용이 1000원인지 확인하세요. CPI 추이를 확인하세요.");
    analysis.put("watchPoint", date + " 발표를 확인하세요.");
    respond(analysis);
    var result = client.analyze(CONTENT + " 비용은 1,000 원이다. " + date + " 발표 예정이다.");
    assertThat(result.getRisk()).contains("1000원");
    assertThat(result.getWatchPoint()).contains(date);
  }

  @Test
  @DisplayName("근거가 부족한 기사에서 구체적인 위험을 만들어 내지 않은 응답을 허용한다")
  void acceptsExplicitInsufficientEvidence() throws Exception {
    String content = "회사가 다음 달 설명회를 열 계획이라고 발표했다.";
    respond(
        Map.of(
            "risk",
            "구체적인 발표 내용이 없어 시장 위험을 판단하기 어려워요.",
            "watchPoint",
            "사업에 미칠 영향을 살펴보려면 설명회의 실제 발표 내용을 확인하세요.",
            "riskEvidence",
            content,
            "watchPointEvidence",
            content));
    assertThat(client.analyze(content).getRisk()).contains("판단하기 어려워요");
  }

  @Test
  @DisplayName("API 키나 본문이 없으면 외부 요청을 보내지 않는다")
  void rejectsMissingInputsBeforeRequest() {
    var noKey =
        new OpenAiMarketAnalysisClient(
            RestClient.builder(), server.url("/").toString(), "", "gpt-test");
    assertThatThrownBy(() -> noKey.analyze(CONTENT)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> client.analyze(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  @DisplayName("OpenAI 서버 오류는 호출자에게 전달한다")
  void propagatesServerError() {
    server.enqueue(new MockResponse().setResponseCode(500));
    assertThatThrownBy(() -> client.analyze(CONTENT)).isInstanceOf(HttpServerErrorException.class);
  }

  @Test
  @DisplayName("손상된 JSON은 분석으로 제공하지 않는다")
  void rejectsMalformedOutput() throws Exception {
    respondText("not-json");
    assertThatThrownBy(() -> client.analyze(CONTENT)).isInstanceOf(IllegalStateException.class);
  }

  private Map<String, Object> validAnalysis() {
    return new LinkedHashMap<>(
        Map.of(
            "risk",
            RISK,
            "watchPoint",
            WATCH_POINT,
            "riskEvidence",
            "물가 재상승 가능성은 남아 있다.",
            "watchPointEvidence",
            "다음 달 물가 지표가 발표된다."));
  }

  private void respond(Map<String, Object> analysis) throws Exception {
    respondText(mapper.writeValueAsString(analysis));
  }

  private void respondText(String text) throws Exception {
    String response =
        mapper.writeValueAsString(
            Map.of(
                "output",
                List.of(Map.of("content", List.of(Map.of("type", "output_text", "text", text))))));
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(response));
  }
}
