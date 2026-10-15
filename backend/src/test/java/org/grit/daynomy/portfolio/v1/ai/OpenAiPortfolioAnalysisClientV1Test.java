package org.grit.daynomy.portfolio.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioAnalysisClientV1Test {
  @Test
  void preservesOriginalPromptAndPersonalWeightsInOpenAiRequest() throws Exception {
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setResponseCode(500));
      var client =
          new OpenAiPortfolioAnalysisClientV1(
              RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
      assertThatThrownBy(
              () ->
                  client.analyze(
                      List.of(
                          new PortfolioAnalysisTargetV1("삼성전자", new BigDecimal("80")),
                          new PortfolioAnalysisTargetV1("SK하이닉스", new BigDecimal("20")))))
          .isInstanceOf(BusinessException.class);

      var request = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.getPath()).isEqualTo("/responses");
      var mapper = new ObjectMapper();
      var body = mapper.readTree(request.getBody().readUtf8());
      assertThat(body.path("model").asText()).isEqualTo("test-model");
      assertThat(body.path("input").get(0).path("content").asText())
          .contains("사용자의 포트폴리오 자산에 미치는 영향을 분석하세요.")
          .doesNotContain("사용자 보유 비중·수량이나 함께 요청된 다른 종목을 추측하거나");
      var input = mapper.readTree(body.path("input").get(1).path("content").asText());
      assertThat(input.path("assets").get(0).path("weight").decimalValue())
          .isEqualByComparingTo("80");
      assertThat(input.path("assets").get(1).path("weight").decimalValue())
          .isEqualByComparingTo("20");
    }
  }
}
