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
        .andExpect(jsonPath("$.input[0].content").value(containsString("2글자 이상 5글자 이하")))
        .andExpect(jsonPath("$.input[0].content").value(containsString("keyword를 요약·변형·조합")))
        .andExpect(
            jsonPath("$.input[0].content").value(containsString("동일한 keyword를 중복해서 반환하지 마세요.")))
        .andExpect(jsonPath("$.input[0].content").value(containsString("definition은 키워드의 일반적인 뜻")))
        .andExpect(jsonPath("$.input[1].content").value("금리 인하와 기준금리, 채권시장을 설명하는 뉴스 본문입니다."))
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
            jsonPath("$.text.format.schema.properties.keywords.items.properties.keyword.minLength")
                .value(2))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.keyword.maxLength")
                .value(5))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.definition.type")
                .value("string"))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.context.type")
                .value("string"))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.watchPoint.type")
                .value("string"))
        .andRespond(
            withSuccess(
                createResponse(
                    keyword("POLICY", "금리 인하"),
                    keyword("POLICY", "기준금리"),
                    keyword("TREND", "채권시장")),
                MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("금리 인하와 기준금리, 채권시장을 설명하는 뉴스 본문입니다.");

    assertThat(keywords).hasSize(3);
    assertThat(keywords.get(0).getCategory()).isEqualTo(KeywordCategory.POLICY);
    assertThat(keywords.get(0).getKeyword()).isEqualTo("금리 인하");
    assertThat(keywords.get(0).getDefinition()).isEqualTo("키워드의 쉬운 뜻");
    assertThat(keywords.get(0).getContext()).isEqualTo("기사에서 키워드가 갖는 의미");
    assertThat(keywords.get(0).getWatchPoint()).isEqualTo("추가로 확인할 사항");
    assertThat(keywords.get(1).getKeyword()).isEqualTo("기준금리");
    server.verify();
  }

  @Test
  @DisplayName("공백 제거 후 길이, 본문 포함 여부와 중복 조건을 만족하지 않는 키워드를 제외한다")
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
                    keyword("TREND", "채권시장"),
                    keyword("TERM", "공모가")),
                MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("금리 인하와 기준금리 인하, 채권시장과 공모가를 설명합니다.");

    assertThat(keywords)
        .extracting(NewsKeyword::getKeyword)
        .containsExactly("금리 인하", "채권시장", "공모가");
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
        {"category":"%s","keyword":"%s","definition":"키워드의 쉬운 뜻","context":"기사에서 키워드가 갖는 의미","watchPoint":"추가로 확인할 사항"}
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
