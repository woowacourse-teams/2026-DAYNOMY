package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiNewsTopicDeduplicatorTest {

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
  @DisplayName("기존 사건과 중복된 후보만 제외한다")
  void removesDuplicateTopic() throws Exception {
    server.enqueue(
        jsonResponse(
            response(
                List.of(
                    Map.of("candidateIndex", 0, "duplicateOf", "topic:7"), nullableResult(1)))));
    NewsGenerationTopic recent = mock(NewsGenerationTopic.class);
    given(recent.getId()).willReturn(7L);
    given(recent.getTitle()).willReturn("기존 금리 결정");
    given(recent.getReferenceDate()).willReturn(LocalDate.of(2026, 11, 6));
    given(recent.getCategory()).willReturn(Category.STOCK);
    given(recent.getFacts()).willReturn(List.of("사실 1", "사실 2", "사실 3"));
    EconomicNewsResearch duplicate = research("같은 금리 결정");
    EconomicNewsResearch unique = research("새로운 공급 계약");

    List<EconomicNewsResearch> result =
        deduplicator().removeDuplicates(List.of(duplicate, unique), List.of(recent));

    assertThat(result).containsExactly(unique);
    String request = server.takeRequest().getBody().readUtf8();
    assertThat(request)
        .contains("gpt-6.1-terra")
        .contains("news_topic_deduplication")
        .contains("기존 금리 결정")
        .contains("동일한 현실 사건")
        .doesNotContain("web_search");
  }

  private OpenAiNewsTopicDeduplicator deduplicator() {
    OpenAiProperties properties =
        new OpenAiProperties(
            "test-key",
            server.url("/v1").toString(),
            "image-model",
            "news-model",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    return new OpenAiNewsTopicDeduplicator(
        properties, org.springframework.web.client.RestClient.builder());
  }

  private EconomicNewsResearch research(String title) {
    return new EconomicNewsResearch(
        title,
        LocalDate.of(2026, 11, 6),
        Category.STOCK,
        List.of("사실 1", "사실 2", "사실 3"),
        List.of(
            new NewsSourceInfo("출처 1", "https://example.com/1"),
            new NewsSourceInfo("출처 2", "https://example.com/2")));
  }

  private Map<String, Object> nullableResult(int candidateIndex) {
    Map<String, Object> value = new java.util.HashMap<>();
    value.put("candidateIndex", candidateIndex);
    value.put("duplicateOf", null);
    return value;
  }

  private String response(List<Map<String, Object>> results) throws Exception {
    String outputText = objectMapper.writeValueAsString(Map.of("results", results));
    return objectMapper.writeValueAsString(
        Map.of(
            "output",
            List.of(
                Map.of("content", List.of(Map.of("type", "output_text", "text", outputText))))));
  }

  private MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }
}
