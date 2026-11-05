package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiContentTranslatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("외부 콘텐츠 제목과 설명을 OpenAI로 한국어 번역한다")
  void translateReturnsTitleAndDescription() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"output":[{"content":[{"type":"output_text","text":"{\\\"title\\\":\\\"번역 제목\\\",\\\"description\\\":\\\"번역 설명\\\"}"}]}]}
                """));

    ContentTranslation result =
        translator(Duration.ofSeconds(1), "test-key").translate("Title", "Description");

    assertThat(result).isEqualTo(new ContentTranslation("번역 제목", "번역 설명"));
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    var body = objectMapper.readTree(request.getBody().readUtf8());
    assertThat(body.path("model").asText()).isEqualTo("test-model");
    assertThat(body.path("input").asText()).contains("Title", "Description");
    assertThat(body.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
  }

  @Test
  @DisplayName("설명이 없는 외부 콘텐츠는 제목만 번역한다")
  void translateAllowsEmptyDescription() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"output":[{"content":[{"type":"output_text","text":"{\\\"title\\\":\\\"번역 제목\\\",\\\"description\\\":\\\"\\\"}"}]}]}
                """));

    ContentTranslation result =
        translator(Duration.ofSeconds(1), "test-key").translate("Title", "");

    assertThat(result).isEqualTo(new ContentTranslation("번역 제목", ""));
  }

  @Test
  @DisplayName("원문 설명이 없으면 번역 응답에 추가된 설명을 거부한다")
  void translateRejectsAddedDescription() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"output":[{"content":[{"type":"output_text","text":"{\\\"title\\\":\\\"번역 제목\\\",\\\"description\\\":\\\"추가 설명\\\"}"}]}]}
                """));

    assertThatThrownBy(
            () -> translator(Duration.ofSeconds(1), "test-key").translate("Title", ""))
        .isInstanceOf(OpenAiContentTranslationException.class);
  }

  @Test
  @DisplayName("OpenAI 오류 응답을 번역 예외로 변환한다")
  void translateConvertsProviderFailure() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertThatThrownBy(
            () -> translator(Duration.ofSeconds(1), "test-key").translate("Title", "Description"))
        .isInstanceOf(OpenAiContentTranslationException.class);
  }

  @Test
  @DisplayName("OpenAI 응답 시간이 제한을 넘으면 번역 예외로 변환한다")
  void translateConvertsTimeout() {
    server.enqueue(
        new MockResponse()
            .setHeadersDelay(400, TimeUnit.MILLISECONDS)
            .setBody(
                """
                {"output":[{"content":[{"type":"output_text","text":"{\\\"title\\\":\\\"제목\\\",\\\"description\\\":\\\"설명\\\"}"}]}]}
                """));

    assertThatThrownBy(
            () -> translator(Duration.ofMillis(100), "test-key").translate("Title", "Description"))
        .isInstanceOf(OpenAiContentTranslationException.class);
  }

  @Test
  @DisplayName("API 키가 없으면 외부 요청을 보내지 않는다")
  void translateRejectsMissingApiKey() {
    assertThatThrownBy(
            () -> translator(Duration.ofSeconds(1), "").translate("Title", "Description"))
        .isInstanceOf(OpenAiContentTranslationException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  private OpenAiContentTranslator translator(Duration timeout, String apiKey) {
    return new OpenAiContentTranslator(
        new OpenAiProperties(
            apiKey, server.url("/").toString(), "image-model", "test-model", timeout, timeout),
        RestClient.builder());
  }
}
