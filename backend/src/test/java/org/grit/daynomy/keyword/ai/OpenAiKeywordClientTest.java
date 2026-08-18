package org.grit.daynomy.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        .andExpect(jsonPath("$.input[1].content").value("뉴스 본문입니다."))
        .andRespond(withSuccess(createResponse(), MediaType.APPLICATION_JSON));

    var keywords = client.extractKeywords("뉴스 본문입니다.");

    assertThat(keywords).hasSize(2);
    assertThat(keywords.get(0).getKeyword()).isEqualTo("금리 인하");
    assertThat(keywords.get(0).getDescription()).isEqualTo("대출 수요 회복과 연결됨");
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
                  "text": "{\\"keywords\\":[{\\"keyword\\":\\"금리 인하\\",\\"description\\":\\"대출 수요 회복과 연결됨\\"},{\\"keyword\\":\\"부동산 규제\\",\\"description\\":\\"거래량 회복 기대와 연결됨\\"}]}"
                }
              ]
            }
          ]
        }
        """;
  }
}
