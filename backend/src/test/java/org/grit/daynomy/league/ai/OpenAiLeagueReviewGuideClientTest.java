package org.grit.daynomy.league.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideEvidence;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.service.LeagueReviewEvidenceService.Candidate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiLeagueReviewGuideClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private MockWebServer server;
  private OpenAiLeagueReviewGuideClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client =
        new OpenAiLeagueReviewGuideClient(
            RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void sendsOnlyDecisionContextAndParsesThreeQuestions() throws Exception {
    enqueueQuestions("지금 확인한 내용은 무엇인가요?", "기대와 달랐던 점은 무엇인가요?", "다음에는 어떤 자료를 확인할 건가요?");

    var questions = client.generate(input(), List.of());

    assertThat(questions.actualResult().text()).isEqualTo("지금 확인한 내용은 무엇인가요?");
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    var body = mapper.readTree(request.getBody().readUtf8());
    assertThat(body.path("store").asBoolean()).isFalse();
    assertThat(body.path("tools").isMissingNode()).isTrue();
    assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
    var user = mapper.readTree(body.path("input").get(1).path("content").asText());
    assertThat(user.path("reason").asText()).isEqualTo("비용 감소 기대");
    assertThat(user.toString())
        .doesNotContain(
            "memberId", "nickname", "email", "quantity", "unitPrice", "weeklyReturnRate");
  }

  @Test
  void requestsQuestionConstraintsThatExcludeObservedProviderFormatFailures() throws Exception {
    enqueueQuestions("어떤 결과를 확인했나요?", "기대와 다른 점은 무엇인가요?", "다음에는 무엇을 확인하나요?");
    client.generate(input(), List.of());
    var body = mapper.readTree(server.takeRequest().getBody().readUtf8());
    var properties = body.path("text").path("format").path("schema").path("properties");
    for (String field : List.of("actualResult", "differenceFromExpectation", "nextAction")) {
      String pattern =
          properties.path(field).path("properties").path("text").path("pattern").asText();
      var question = java.util.regex.Pattern.compile(pattern);
      assertThat(question.matcher("어떤 사실을 확인했나요?").matches()).isTrue();
      assertThat(question.matcher("제공된 자료에서는 확인 가능한 사실이 없습니다.").matches()).isFalse();
      assertThat(question.matcher("예상과 다르게 매출은 감소했고(확인한 근거가 없습니까?)").matches()).isFalse();
      assertThat(question.matcher("질문을\n나누면 되나요?").matches()).isFalse();
      assertThat(question.matcher("가".repeat(160) + "?").matches()).isFalse();
    }
  }

  @Test
  void stillRejectsObservedInvalidOutputsIfTheProviderIgnoresTheSchema() throws Exception {
    for (String invalid :
        List.of("제공된 자료에서는 확인 가능한 사실이 없습니다.", "예상과 다르게 매출은 감소했고(확인한 근거가 없습니까?)")) {
      enqueueQuestions(invalid, "기대와 다른 점은 무엇인가요?", "다음에는 무엇을 확인하나요?");
      assertUnavailable();
    }
  }

  @Test
  void rejectsUnsafeOrMalformedQuestions() throws Exception {
    enqueueQuestions("지금 매수하세요?", "기대와 다른 점은?", "다음에는 무엇을 확인하나요?");

    assertThatThrownBy(() -> client.generate(input(), List.of()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_GUIDE_UNAVAILABLE));
  }

  @Test
  void rejectsIncompleteAndUnstructuredResponses() {
    server.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"incomplete\",\"output\":[]}"));
    assertUnavailable();

    server.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(
                "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}"));
    assertUnavailable();
  }

  @Test
  void rejectsArticleIdsOutsideTheRetrievedSet() throws Exception {
    var candidate =
        new Candidate(
            new ReviewGuideEvidence(
                "NEWS:12",
                "실적 기사",
                java.time.Instant.parse("2026-10-20T00:00:00Z"),
                "매출 증가",
                "/news/12"),
            "매출 증가");
    enqueueOutput(
        Map.of(
            "actualResult", Map.of("text", "무엇을 확인했나요?", "evidenceIds", List.of("NEWS:99")),
            "differenceFromExpectation", Map.of("text", "무엇이 달랐나요?", "evidenceIds", List.of()),
            "nextAction", Map.of("text", "다음에는 무엇을 보나요?", "evidenceIds", List.of())));

    assertThatThrownBy(() -> client.generate(input(), List.of(candidate)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_GUIDE_UNAVAILABLE));
  }

  private void assertUnavailable() {
    assertThatThrownBy(() -> client.generate(input(), List.of()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_GUIDE_UNAVAILABLE));
  }

  private void enqueueQuestions(String actual, String difference, String next) throws Exception {
    enqueueOutput(
        Map.of(
            "actualResult", Map.of("text", actual, "evidenceIds", List.of()),
            "differenceFromExpectation", Map.of("text", difference, "evidenceIds", List.of()),
            "nextAction", Map.of("text", next, "evidenceIds", List.of())));
  }

  private void enqueueOutput(Map<String, ?> questions) throws Exception {
    String output = mapper.writeValueAsString(questions);
    String response =
        mapper.writeValueAsString(
            Map.of(
                "status",
                "completed",
                "output",
                List.of(
                    Map.of("content", List.of(Map.of("type", "output_text", "text", output))))));
    server.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(response));
  }

  private OpenAiLeagueReviewGuideClient.Input input() {
    return new OpenAiLeagueReviewGuideClient.Input(
        "예시 종목", "HOLD", "비용 감소 기대", "비용 감소", "비용 증가", null, null, true);
  }
}
