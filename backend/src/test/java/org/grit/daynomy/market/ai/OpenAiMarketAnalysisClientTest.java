package org.grit.daynomy.market.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.grit.daynomy.market.domain.asset.Asset;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiMarketAnalysisClientTest {

  @Test
  @DisplayName("뉴스 본문을 OpenAI에 전달하고 시장 분석을 파싱한다")
  void analyzeCallsOpenAiAndParsesResponse() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    OpenAiMarketAnalysisClient client =
        new OpenAiMarketAnalysisClient(
            restClientBuilder, "https://api.openai.test", "test-api-key", "gpt-test");
    server
        .expect(requestTo("https://api.openai.test/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
        .andExpect(jsonPath("$.model").value("gpt-test"))
        .andExpect(jsonPath("$.input[1].content").value("뉴스 본문입니다."))
        .andRespond(withSuccess(createResponse(), MediaType.APPLICATION_JSON));

    var analysis = client.analyze("뉴스 본문입니다.");

    assertThat(analysis.getCause()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
    assertThat(analysis.getAssets()).hasSize(2);
    assertThat(analysis.getAssets().get(0).getAsset()).isEqualTo(Asset.STOCK);
    assertThat(analysis.getAssets().get(0).getDirection()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(analysis.getAssets().get(0).getImpactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(analysis.getScenarios()).hasSize(3);
    assertThat(analysis.getScenarios().get(0).getTimeHorizon()).isEqualTo(TimeHorizon.SHORT_TERM);
    assertThat(analysis.getScenarios().get(0).getProbability()).isEqualTo(70);
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

  private String createResponse() {
    return """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\\"cause\\":\\"금리 인하 기대가 위험자산 선호를 높입니다.\\",\\"assets\\":[{\\"asset\\":\\"STOCK\\",\\"direction\\":\\"POSITIVE\\",\\"impactLevel\\":\\"HIGH\\",\\"reason\\":\\"할인율 하락 기대가 주식 밸류에이션에 긍정적입니다.\\"},{\\"asset\\":\\"GOLD\\",\\"direction\\":\\"POSITIVE\\",\\"impactLevel\\":\\"MEDIUM\\",\\"reason\\":\\"실질금리 하락 기대가 금 가격에 우호적입니다.\\"}],\\"scenarios\\":[{\\"timeHorizon\\":\\"SHORT_TERM\\",\\"prediction\\":\\"단기적으로 주식 선호가 개선될 수 있습니다.\\",\\"probability\\":70,\\"reason\\":\\"금리 인하 기대가 투자 심리를 자극하기 때문입니다.\\"},{\\"timeHorizon\\":\\"MID_TERM\\",\\"prediction\\":\\"중기적으로 정책 강도에 따라 자산별 차별화가 나타날 수 있습니다.\\",\\"probability\\":55,\\"reason\\":\\"실제 정책 집행 속도에 불확실성이 있기 때문입니다.\\"},{\\"timeHorizon\\":\\"LONG_TERM\\",\\"prediction\\":\\"장기적으로 경기 흐름이 자산 가격을 좌우할 수 있습니다.\\",\\"probability\\":45,\\"reason\\":\\"뉴스 본문만으로 장기 경로를 단정하기 어렵기 때문입니다.\\"}]}"
                }
              ]
            }
          ]
        }
        """;
  }
}
