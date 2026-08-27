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
    OpenAiKeywordClient client =
        new OpenAiKeywordClient(
            restClientBuilder, "https://api.openai.test", "test-api-key", "gpt-test");
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
        .andExpect(jsonPath("$.model").value("gpt-test"))
        .andExpect(jsonPath("$.input[0].content").value(containsString(KeywordCategory.DEFINITION)))
        .andExpect(jsonPath("$.input[1].content").value("뉴스 본문입니다."))
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
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.points.minItems")
                .value(3))
        .andExpect(
            jsonPath("$.text.format.schema.properties.keywords.items.properties.points.maxItems")
                .value(3))
        .andRespond(withSuccess(createResponse(), MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("뉴스 본문입니다.");

    assertThat(keywords).hasSize(2);
    assertThat(keywords.get(0).getCategory()).isEqualTo(KeywordCategory.POLICY);
    assertThat(keywords.get(0).getKeyword()).isEqualTo("금리 인하");
    assertThat(keywords.get(0).getPoint1()).isEqualTo("기준금리 인하는 대출 이자 부담을 낮춥니다.");
    assertThat(keywords.get(0).getPoint2()).isEqualTo("소비와 투자 회복 기대를 높일 수 있습니다.");
    assertThat(keywords.get(0).getPoint3()).isEqualTo("자산 가격 변동에도 영향을 줄 수 있습니다.");
    assertThat(keywords.get(1).getKeyword()).isEqualTo("부동산 규제");
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

  private String createResponse() {
    return """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\\"keywords\\":[{\\"category\\":\\"POLICY\\",\\"keyword\\":\\"금리 인하\\",\\"points\\":[\\"기준금리 인하는 대출 이자 부담을 낮춥니다.\\",\\"소비와 투자 회복 기대를 높일 수 있습니다.\\",\\"자산 가격 변동에도 영향을 줄 수 있습니다.\\"]},{\\"category\\":\\"POLICY\\",\\"keyword\\":\\"부동산 규제\\",\\"points\\":[\\"주택 거래 조건에 영향을 줍니다.\\",\\"대출 수요 변화를 일으킬 수 있습니다.\\",\\"시장 참여자의 심리를 바꿀 수 있습니다.\\"]}]}"
                }
              ]
            }
          ]
        }
        """;
  }
}
