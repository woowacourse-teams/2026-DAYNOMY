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
        .andExpect(jsonPath("$.input[0].content").value(containsString("뉴스 본문에서 직접 확인할 수 있는 사실")))
        .andExpect(
            jsonPath("$.input[0].content").value(containsString("시장 전체의 확정적인 신호로 일반화하지 마세요")))
        .andExpect(jsonPath("$.input[1].content").value("뉴스 본문입니다."))
        .andExpect(jsonPath("$.text.format.schema.properties.observedFact.type").value("string"))
        .andExpect(jsonPath("$.text.format.schema.properties.interpretation.type").value("string"))
        .andExpect(jsonPath("$.text.format.schema.properties.risk.type").value("string"))
        .andExpect(jsonPath("$.text.format.schema.properties.watchPoint.type").value("string"))
        .andExpect(jsonPath("$.text.format.schema.properties.limitation.type").value("string"))
        .andRespond(withSuccess(createResponse(), MediaType.APPLICATION_JSON));

    var analysis = client.analyze("뉴스 본문입니다.");

    assertThat(analysis.getObservedFact()).isEqualTo("기준금리가 인하됐습니다.");
    assertThat(analysis.getInterpretation()).isEqualTo("위험자산 선호가 높아질 수 있습니다.");
    assertThat(analysis.getRisk()).isEqualTo("물가가 다시 상승하면 해석이 달라질 수 있습니다.");
    assertThat(analysis.getWatchPoint()).isEqualTo("다음 소비자물가 발표를 확인해야 합니다.");
    assertThat(analysis.getLimitation()).isEqualTo("자산별 가격 데이터는 포함하지 않았습니다.");
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

  @Test
  @DisplayName("시장 분석 필드가 비어 있으면 예외를 던진다")
  void analyzeThrowsWhenAnalysisFieldIsBlank() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            restClientBuilder, "https://api.openai.test/v1", "test-api-key", "gpt-test");
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andRespond(withSuccess(createBlankFieldResponse(), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.analyze("뉴스 본문입니다."))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenAI market analysis response must contain risk.");
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
                  "text": "{\\"observedFact\\":\\"기준금리가 인하됐습니다.\\",\\"interpretation\\":\\"위험자산 선호가 높아질 수 있습니다.\\",\\"risk\\":\\"물가가 다시 상승하면 해석이 달라질 수 있습니다.\\",\\"watchPoint\\":\\"다음 소비자물가 발표를 확인해야 합니다.\\",\\"limitation\\":\\"자산별 가격 데이터는 포함하지 않았습니다.\\"}"
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

  private String createBlankFieldResponse() {
    return """
        {
          "output": [
            {
              "content": [
                {
                  "type": "output_text",
                  "text": "{\\"observedFact\\":\\"사실\\",\\"interpretation\\":\\"해석\\",\\"risk\\":\\"\\",\\"watchPoint\\":\\"확인 지표\\",\\"limitation\\":\\"분석 한계\\"}"
                }
              ]
            }
          ]
        }
        """;
  }
}
