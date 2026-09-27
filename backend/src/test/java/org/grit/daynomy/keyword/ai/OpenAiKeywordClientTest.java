package org.grit.daynomy.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiKeywordClientTest {

  @Test
  @DisplayName("뉴스 본문을 OpenAI에 전달하고 키워드 목록을 파싱한다")
  void extractKeywordsCallsOpenAiAndParsesResponse() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiKeywordClient client = createClient(restClientBuilder);
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
        .andExpect(jsonPath("$.model").value("gpt-test"))
        .andExpect(jsonPath("$.input[0].content").value(containsString(KeywordCategory.DEFINITION)))
        .andExpect(
            jsonPath("$.input[0].content")
                .value(containsString("뉴스 본문에 연속으로 존재하는 핵심 명사구를 글자 그대로 복사")))
        .andExpect(jsonPath("$.input[0].content").value(containsString("keyword를 요약·변형·조합")))
        .andExpect(
            jsonPath("$.input[0].content").value(containsString("동일한 keyword를 중복해서 반환하지 마세요.")))
        .andExpect(jsonPath("$.input[1].content").value("금리 인하와 부동산 규제, 채권시장을 설명하는 뉴스 본문입니다."))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.category.type")
                .value("string"))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.category.enum[0]")
                .value("PERSON"))
        .andExpect(
            jsonPath(
                    "$.text.format.schema.properties.keywords.items.properties.category.description")
                .value(KeywordCategory.DEFINITION))
        .andExpect(jsonPath("$.text.format.schema.properties.keywords.minItems").value(3))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.points.minItems")
                .value(3))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.points.maxItems")
                .value(3))
        .andRespond(
            withSuccess(
                createResponse(
                    keyword("POLICY", "금리 인하"),
                    keyword("POLICY", "부동산 규제"),
                    keyword("TREND", "채권시장")),
                MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("금리 인하와 부동산 규제, 채권시장을 설명하는 뉴스 본문입니다.");

    assertThat(keywords).hasSize(3);
    assertThat(keywords.get(0).getCategory()).isEqualTo(KeywordCategory.POLICY);
    assertThat(keywords.get(0).getKeyword()).isEqualTo("금리 인하");
    assertThat(keywords.get(0).getPoint1()).isEqualTo("첫 번째 분석 포인트");
    assertThat(keywords.get(0).getPoint2()).isEqualTo("두 번째 분석 포인트");
    assertThat(keywords.get(0).getPoint3()).isEqualTo("세 번째 분석 포인트");
    assertThat(keywords.get(1).getKeyword()).isEqualTo("부동산 규제");
    server.verify();
  }

  @Test
  @DisplayName("키워드의 공백을 제거하고 본문에 없는 키워드와 중복 키워드를 제외한다")
  void extractKeywordsFiltersInvalidAndDuplicateKeywords() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiKeywordClient client = createClient(restClientBuilder);
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andRespond(
            withSuccess(
                createResponse(
                    keyword("POLICY", " 금리 인하 "),
                    keyword("POLICY", "금리 인하"),
                    keyword("POLICY", "기준금리 인하"),
                    keyword("POLICY", "부동산 규제"),
                    keyword("TREND", "채권시장")),
                MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("금리 인하와 부동산 규제, 채권시장을 설명합니다.");

    assertThat(keywords)
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("금리 인하", "부동산 규제", "채권시장");
    server.verify();
  }

  @Test
  @DisplayName("유효한 키워드가 3개 미만이면 예외를 던진다")
  void extractKeywordsThrowsWhenValidKeywordsAreFewerThanThree() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiKeywordClient client = createClient(restClientBuilder);
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andRespond(
            withSuccess(
                createResponse(
                    keyword("POLICY", "금리 인하"),
                    keyword("POLICY", " 금리 인하 "),
                    keyword("POLICY", "기준금리 인하")),
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.extractKeywords("금리 인하를 설명합니다."))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI keyword response must contain at least 3 valid keywords.");
    server.verify();
  }

  @Test
  @DisplayName("OpenAI API 키가 없으면 키워드 추출 요청 전에 예외를 던진다")
  void extractKeywordsThrowsWhenApiKeyMissing() {
    OpenAiKeywordClient client =
        new OpenAiKeywordClient(RestClient.builder(), "https://api.openai.test", "", "gpt-test");

    assertThatThrownBy(() -> client.extractKeywords("뉴스 본문입니다."))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OPENAI_API_KEY is required to extract news keywords.");
  }

  private OpenAiKeywordClient createClient(RestClient.Builder restClientBuilder) {
    return new OpenAiKeywordClient(
        restClientBuilder, "https://api.openai.test/v1", "test-api-key", "gpt-test");
  }

  private String keyword(String category, String keyword) {
    return """
        {"category":"%s","keyword":"%s","points":["첫 번째 분석 포인트","두 번째 분석 포인트","세 번째 분석 포인트"]}
        """
        .formatted(category, keyword)
        .strip();
  }

  private String createResponse(String... keywords) {
    String outputText = "{\"keywords\":[" + String.join(",", keywords) + "]}";
    String escapedOutputText = outputText.replace("\\", "\\\\").replace("\"", "\\\"");

    return """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "%s"
                }
              ]
            }
          ]
        }
        """
        .formatted(escapedOutputText);
  }
}
