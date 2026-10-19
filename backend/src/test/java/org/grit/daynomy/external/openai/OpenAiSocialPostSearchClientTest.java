package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiSocialPostSearchClientTest {

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
  void searchKeepsPostsRegardlessOfPublishedDate() throws Exception {
    LocalDate today = LocalDate.now();
    LocalDate oldDate = today.minusYears(1);
    server.enqueue(
        jsonResponse(
            searchResponse(
                List.of(
                    Map.of(
                        "title",
                        "  삼성전자   실적 전망  ",
                        "url",
                        "https://www.threads.net/@investor/post/ABC?utm_source=test"),
                    Map.of("title", "", "url", "https://x.com/market/status/12345?s=20"),
                    Map.of("title", "삼성전자 반도체", "url", "https://www.threads.com/t/DEF/"),
                    Map.of("title", "프로필", "url", "https://x.com/market"),
                    Map.of(
                        "title", "허용되지 않은 도메인", "url", "https://example.com/market/status/12345")),
                List.of(
                    Map.of(
                        "title",
                        "삼성전자 실적 전망",
                        "url",
                        "https://www.threads.net/@investor/post/ABC",
                        "publishedDate",
                        today.toString()),
                    Map.of(
                        "title",
                        "삼성전자 수급",
                        "url",
                        "https://x.com/market/status/12345",
                        "publishedDate",
                        oldDate.toString()),
                    Map.of(
                        "title",
                        "삼성전자 반도체",
                        "url",
                        "https://www.threads.com/t/DEF",
                        "publishedDate",
                        ""),
                    Map.of(
                        "title",
                        "출처에 없는 게시물",
                        "url",
                        "https://x.com/other/status/999",
                        "publishedDate",
                        today.toString())))));
    OpenAiSocialPostSearchClient client = newClient("test-key");

    var candidates = client.search("삼성전자");

    assertThat(candidates)
        .extracting(SocialPostCandidate::platform)
        .containsExactly(
            SocialPostCandidate.Platform.THREADS,
            SocialPostCandidate.Platform.X,
            SocialPostCandidate.Platform.THREADS);
    assertThat(candidates.get(0).title()).isEqualTo("삼성전자 실적 전망");
    assertThat(candidates.get(0).url()).isEqualTo("https://www.threads.net/@investor/post/ABC");
    assertThat(candidates.get(0).publishedDate()).isEqualTo(today.toString());
    assertThat(candidates.get(1).url()).isEqualTo("https://x.com/market/status/12345");
    assertThat(candidates.get(1).title()).isEqualTo("삼성전자 수급");
    assertThat(candidates.get(1).publishedDate()).isEqualTo(oldDate.toString());
    assertThat(candidates.get(2).url()).isEqualTo("https://www.threads.com/t/DEF");
    assertThat(candidates.get(2).publishedDate()).isEmpty();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    assertThat(requestBody.path("model").asText()).isEqualTo("test-social-model");
    assertThat(requestBody.path("tool_choice").asText()).isEqualTo("required");
    assertThat(requestBody.path("include").get(0).asText())
        .isEqualTo("web_search_call.action.sources");
    assertThat(requestBody.path("input").asText())
        .contains("person, company, topic")
        .contains("Search both platforms")
        .contains("Never guess a date")
        .doesNotContain(today.toString(), oldDate.toString());
    assertThat(requestBody.path("text").path("format").path("name").asText())
        .isEqualTo("social_post_search");
    JsonNode domains = requestBody.path("tools").get(0).path("filters").path("allowed_domains");
    assertThat(domains.get(0).asText()).isEqualTo("threads.net");
    assertThat(domains.get(1).asText()).isEqualTo("threads.com");
    assertThat(domains.get(2).asText()).isEqualTo("x.com");
    assertThat(domains.get(3).asText()).isEqualTo("twitter.com");
  }

  @Test
  void profileSearchKeepsOnlyPostsAuthoredByConfiguredProfiles() throws Exception {
    server.enqueue(
        jsonResponse(
            searchResponse(
                List.of(
                    Map.of("title", "official", "url", "https://x.com/elonmusk/status/12345"),
                    Map.of("title", "old", "url", "https://x.com/elonmusk/status/11111"),
                    Map.of("title", "unknown", "url", "https://x.com/elonmusk/status/22222"),
                    Map.of("title", "mention", "url", "https://x.com/other/status/67890"),
                    Map.of("title", "threads", "url", "https://www.threads.net/@elonmusk/post/ABC"),
                    Map.of("title", "short", "url", "https://www.threads.net/t/DEF")),
                List.of(
                    Map.of(
                        "title",
                        "official",
                        "url",
                        "https://x.com/elonmusk/status/12345",
                        "publishedDate",
                        "2026-10-12"),
                    Map.of(
                        "title",
                        "old",
                        "url",
                        "https://x.com/elonmusk/status/11111",
                        "publishedDate",
                        "2026-10-09"),
                    Map.of(
                        "title",
                        "unknown",
                        "url",
                        "https://x.com/elonmusk/status/22222",
                        "publishedDate",
                        ""),
                    Map.of(
                        "title",
                        "mention",
                        "url",
                        "https://x.com/other/status/67890",
                        "publishedDate",
                        "2026-10-12"),
                    Map.of(
                        "title",
                        "threads",
                        "url",
                        "https://www.threads.net/@elonmusk/post/ABC",
                        "publishedDate",
                        "2026-10-12"),
                    Map.of(
                        "title",
                        "short",
                        "url",
                        "https://www.threads.net/t/DEF",
                        "publishedDate",
                        "2026-10-12")))));
    OpenAiSocialPostSearchClient client = newClient("test-key");

    var candidates =
        client.searchProfiles(
            "https://x.com/elonmusk",
            "https://www.threads.net/@elonmusk",
            LocalDate.parse("2026-10-10"),
            LocalDate.parse("2026-10-12"));

    assertThat(candidates)
        .extracting(SocialPostCandidate::url)
        .containsExactly(
            "https://x.com/elonmusk/status/12345", "https://www.threads.net/@elonmusk/post/ABC");
    JsonNode requestBody = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
    assertThat(requestBody.path("input").asText())
        .contains("authored only by these exact official profiles")
        .contains("https://x.com/elonmusk")
        .contains("https://www.threads.net/@elonmusk")
        .contains("2026-10-10", "2026-10-12");
  }

  @Test
  void blankKeywordDoesNotCallOpenAi() {
    assertThat(newClient("test-key").search("  ")).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void missingApiKeyIsReportedWithoutCallingOpenAi() {
    assertThatThrownBy(() -> newClient("").search("삼성전자"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void invalidResponseIsReported() {
    server.enqueue(jsonResponse("{}"));

    assertThatThrownBy(() -> newClient("test-key").search("삼성전자"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED);
  }

  private OpenAiSocialPostSearchClient newClient(String apiKey) {
    return new OpenAiSocialPostSearchClient(
        new OpenAiProperties(
            apiKey,
            server.url("/v1").toString(),
            "test-image-model",
            "test-news-model",
            "test-social-model",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)),
        org.springframework.web.client.RestClient.builder());
  }

  private MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private String searchResponse(List<Map<String, String>> sources, List<Map<String, String>> items)
      throws Exception {
    String outputText = objectMapper.writeValueAsString(Map.of("items", items));
    return objectMapper.writeValueAsString(
        Map.of(
            "output",
            List.of(
                Map.of("type", "web_search_call", "action", Map.of("sources", sources)),
                Map.of(
                    "type",
                    "message",
                    "content",
                    List.of(Map.of("type", "output_text", "text", outputText))))));
  }
}
