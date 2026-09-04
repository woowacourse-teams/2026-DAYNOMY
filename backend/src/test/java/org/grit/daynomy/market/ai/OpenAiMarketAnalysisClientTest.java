package org.grit.daynomy.market.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class OpenAiMarketAnalysisClientTest {

  @Test
  @DisplayName("뉴스 본문을 OpenAI에 전달하고 시장 분석을 파싱한다")
  void analyzeCallsOpenAiAndParsesResponse() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            restClientBuilder, "https://api.openai.test/v1", "test-api-key", "gpt-test");
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
        .andExpect(jsonPath("$.model").value("gpt-test"))
        .andExpect(jsonPath("$.input[0].content").value(containsString("발생 원인과 이 이슈가 시장에서 중요한 이유")))
        .andExpect(jsonPath("$.input[1].content").value("뉴스 본문입니다."))
        .andExpect(jsonPath("$.text.format.schema.properties.summary.type").value("string"))
        .andExpect(jsonPath("$.text.format.schema.required[0]").value("summary"))
        .andExpect(jsonPath("$.text.format.schema.properties.cause").doesNotExist())
        .andExpect(jsonPath("$.text.format.schema.properties.importance").doesNotExist())
        .andExpect(jsonPath("$.text.format.schema.properties.assets").doesNotExist())
        .andExpect(jsonPath("$.text.format.schema.properties.scenarios").doesNotExist())
        .andRespond(withSuccess(createResponse(), MediaType.APPLICATION_JSON));

    var analysis = client.analyze("뉴스 본문입니다.");

    assertThat(analysis.getSummary())
        .isEqualTo("금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.");
    assertThat(analysis.getAssets()).isEmpty();
    assertThat(analysis.getScenarios()).isEmpty();
    server.verify();
  }

  @Test
  @DisplayName("OpenAI API 키가 없으면 시장 분석 요청 전에 예외를 던진다")
  void analyzeThrowsWhenApiKeyMissing() {
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            RestClient.builder(), "https://api.openai.test", "", "gpt-test");

    assertThatThrownBy(() -> client.analyze("뉴스 본문입니다."))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OPENAI_API_KEY is required to analyze news market impact.");
  }

  @Test
  @DisplayName("OpenAI API가 서버 오류를 반환하면 HTTP 예외를 전달한다")
  void analyzePropagatesOpenAiServerError() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            restClientBuilder, "https://api.openai.test/v1", "test-api-key", "gpt-test");
    server.expect(requestTo("https://api.openai.test/v1/responses")).andRespond(withServerError());

    assertThatThrownBy(() -> client.analyze("뉴스 본문입니다."))
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  @DisplayName("OpenAI output_text가 올바른 JSON이 아니면 파싱 예외를 던진다")
  void analyzeThrowsWhenOutputTextIsMalformed() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            restClientBuilder, "https://api.openai.test/v1", "test-api-key", "gpt-test");
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andRespond(withSuccess(createMalformedResponse(), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.analyze("뉴스 본문입니다."))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to parse OpenAI market analysis response.");
    server.verify();
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
                  "text": "{\\"summary\\":\\"금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격에 영향을 줍니다.\\"}"
                }
              ]
            }
          ]
        }
        """;
  }

  private String createMalformedResponse() {
    return """
        {
          "output": [
            {
              "content": [
                {
                  "type": "output_text",
                  "text": "not-json"
                }
              ]
            }
          ]
        }
        """;
  }
}
