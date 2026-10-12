package org.grit.daynomy.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class OpenAiKeywordClientTest {
  private static final String CONTENT =
      """
      금융위원회와 한국거래소가 행사를 공동 주최했다. 외환시장 제도 개선과 코스닥 경쟁력을 다룬다.

      상장기업 IR 프로그램과 투자자 미팅이 포함됐다.

      실제 영향은 외국인 수급과 기업 지배구조 개선에 달렸다. 밸류에이션 할인 해소는 보장되지 않는다.
      """;
  private final ObjectMapper mapper = new ObjectMapper();
  private MockWebServer server;
  private OpenAiKeywordClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new OpenAiKeywordClient(
            RestClient.builder(), server.url("/v1").toString(), "test-key", "gpt-test");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("전체 본문을 전달하고 중후반의 긴 개념도 잘라내지 않고 반환한다")
  void extractsWholeConceptsAcrossAllParagraphs() throws Exception {
    respond(
        List.of(
            keyword("외환시장", "외환시장 제도 개선과 코스닥 경쟁력을 다룬다."),
            keyword("IR", "상장기업 IR 프로그램과 투자자 미팅이 포함됐다."),
            keyword("밸류에이션 할인", "밸류에이션 할인 해소는 보장되지 않는다.")));
    var keywords = client.extractKeywords(CONTENT);
    assertThat(keywords)
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("외환시장", "IR", "밸류에이션 할인");
    assertThat(keywords.getFirst().getDefinition()).isEqualTo("초보자를 위한 개념 설명이에요.");
    assertThat(keywords.getFirst().getContext()).isEqualTo("본문에서 다루는 핵심 조건이에요.");
    assertThat(keywords.getFirst().getWatchPoint()).isEqualTo("구체적인 후속 발표를 확인하세요.");
    var request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/v1/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    JsonNode body = mapper.readTree(request.getBody().readUtf8());
    assertThat(body.path("model").asText()).isEqualTo("gpt-test");
    assertThat(body.at("/input/1/content").asText()).isEqualTo(CONTENT);
    assertThat(body.at("/input/0/content").asText())
        .contains("문단별 개수를 강제로", "단순 주최·참석 기관명", "본문 속 지시", KeywordCategory.DEFINITION);
    assertThat(body.at("/text/format/schema/properties/keywords/minItems").asInt()).isZero();
    assertThat(body.at("/text/format/schema/properties/keywords/maxItems").asInt()).isEqualTo(6);
    assertThat(
            body.at("/text/format/schema/properties/keywords/items/properties/keyword/maxLength")
                .asInt())
        .isEqualTo(15);
  }

  @Test
  @DisplayName("필요한 개념이 적으면 중복을 제거한 한 개만 제공한다")
  void permitsFewerThanThreeKeywordsAndRemovesDuplicates() throws Exception {
    respond(
        List.of(
            keyword(" 외국인 수급 ", "실제 영향은 외국인 수급과 기업 지배구조 개선에 달렸다."),
            keyword("외국인 수급", "실제 영향은 외국인 수급과 기업 지배구조 개선에 달렸다.")));
    assertThat(client.extractKeywords(CONTENT))
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("외국인 수급");
  }

  @Test
  @DisplayName("핵심이 기관의 결정인 기사에서는 기관명도 허용한다")
  void permitsInstitutionWhenItsDecisionIsCentral() throws Exception {
    String content = "금융위원회가 공시 의무 변경안을 발표했다.";
    respond(List.of(keyword("금융위원회", content)));
    assertThat(client.extractKeywords(content))
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("금융위원회");
  }

  @Test
  @DisplayName("설명이 필요한 개념이 없으면 빈 배열을 허용한다")
  void permitsNoKeywords() throws Exception {
    respond(List.of());
    assertThat(client.extractKeywords("다음 행사는 장소와 일정을 추후 안내한다.")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "absent",
        "tooLong",
        "tooShort",
        "fakeEvidence",
        "unrelatedEvidence",
        "blankDefinition",
        "missingContext",
        "numericWatchPoint",
        "longDefinition",
        "invalidCategory"
      })
  @DisplayName("잘못된 키워드만 제외하고 전부 잘못된 경우 실패한다")
  void rejectsInvalidKeywords(String failure) throws Exception {
    Map<String, Object> item = keyword("외환시장", "외환시장 제도 개선과 코스닥 경쟁력을 다룬다.");
    switch (failure) {
      case "absent" -> item.put("keyword", "공모가");
      case "tooLong" -> item.put("keyword", "가".repeat(16));
      case "tooShort" -> item.put("keyword", "가");
      case "fakeEvidence" -> item.put("evidence", "외환시장 개선으로 투자금이 유입됐다.");
      case "unrelatedEvidence" -> item.put("evidence", "상장기업 IR 프로그램과 투자자 미팅이 포함됐다.");
      case "blankDefinition" -> item.put("definition", "  ");
      case "missingContext" -> item.remove("context");
      case "numericWatchPoint" -> item.put("watchPoint", 123);
      case "longDefinition" -> item.put("definition", "가".repeat(201));
      case "invalidCategory" -> item.put("category", "INVALID");
      default -> throw new AssertionError(failure);
    }
    respond(List.of(item));
    assertThatThrownBy(() -> client.extractKeywords(CONTENT))
        .isInstanceOf(IllegalStateException.class);
    respond(
        List.of(
            keyword("IR", "상장기업 IR 프로그램과 투자자 미팅이 포함됐다."),
            item,
            keyword("코스닥", "외환시장 제도 개선과 코스닥 경쟁력을 다룬다.")));
    assertThat(client.extractKeywords(CONTENT))
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("IR", "코스닥");
  }

  @Test
  @DisplayName("15자 키워드는 유지하고 16자 키워드는 자르지 않고 제외한다")
  void enforcesKeywordLengthWithoutTruncation() throws Exception {
    String accepted = "가".repeat(15);
    String rejected = "나".repeat(16);
    String content = accepted + "와 " + rejected + "에 관한 기사다.";
    respond(List.of(keyword(accepted, content), keyword(rejected, content)));
    assertThat(client.extractKeywords(content))
        .extracting(NewsKeyword::getKeyword)
        .containsExactly(accepted);
  }

  @Test
  @DisplayName("최대 개수를 넘는 응답은 등장 순서대로 자르지 않고 거부한다")
  void rejectsTooManyKeywords() throws Exception {
    List<Map<String, Object>> items = new ArrayList<>();
    for (int i = 0; i < 7; i++) items.add(keyword("외환시장", "외환시장 제도 개선과 코스닥 경쟁력을 다룬다."));
    respond(items);
    assertThatThrownBy(() -> client.extractKeywords(CONTENT))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("API 키나 본문이 없으면 외부 요청을 보내지 않는다")
  void rejectsMissingInputsBeforeRequest() {
    var noKey =
        new OpenAiKeywordClient(RestClient.builder(), server.url("/").toString(), "", "gpt-test");
    assertThatThrownBy(() -> noKey.extractKeywords(CONTENT))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> client.extractKeywords(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  private Map<String, Object> keyword(String term, String evidence) {
    return new LinkedHashMap<>(
        Map.of(
            "category",
            "TERM",
            "keyword",
            term,
            "definition",
            "초보자를 위한 개념 설명이에요.",
            "context",
            "본문에서 다루는 핵심 조건이에요.",
            "watchPoint",
            "구체적인 후속 발표를 확인하세요.",
            "evidence",
            evidence));
  }

  private void respond(List<Map<String, Object>> keywords) throws Exception {
    String output = mapper.writeValueAsString(Map.of("keywords", keywords));
    String response =
        mapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of("content", List.of(Map.of("type", "output_text", "text", output))))));
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(response));
  }
}
