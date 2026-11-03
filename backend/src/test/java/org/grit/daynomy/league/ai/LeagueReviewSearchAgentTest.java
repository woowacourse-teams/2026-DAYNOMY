package org.grit.daynomy.league.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideEvidence;
import org.grit.daynomy.league.service.LeagueReviewEvidenceService;
import org.grit.daynomy.league.service.LeagueReviewEvidenceService.Candidate;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LeagueReviewSearchAgentTest {
  @Test
  void responsesModelCallsOnlyTheReadOnlySearchToolWithoutStoringTheRequest() throws Exception {
    var service = mock(LeagueReviewEvidenceService.class);
    Instant saved = Instant.parse("2026-10-20T00:00:00Z");
    given(service.search(2L, "삼성전자", saved, "매출")).willReturn(candidates(1, 1));
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .addHeader("Content-Type", "application/json")
              .setBody(
                  """
              {"id":"resp_1","object":"response","created_at":1,"status":"completed",
               "model":"test-model","output":[{"type":"function_call","id":"fc_1",
               "call_id":"call_1","name":"searchPublishedNews",
               "arguments":"{\\"focusTerm\\":\\"매출\\"}","status":"completed"}],
               "usage":{"input_tokens":10,"output_tokens":10,"total_tokens":20}}
              """));
      server.enqueue(
          new MockResponse()
              .addHeader("Content-Type", "application/json")
              .setBody(
                  """
              {"id":"resp_2","object":"response","created_at":2,"status":"completed",
               "model":"test-model","output":[{"type":"message","id":"msg_1",
               "status":"completed","role":"assistant","content":[{"type":"output_text",
               "text":"검색했습니다"}]}],
               "usage":{"input_tokens":10,"output_tokens":10,"total_tokens":20}}
              """));
      var agent =
          new LeagueReviewSearchAgent(
              service, server.url("/").toString(), "test-key", "test-model");
      var input =
          new OpenAiLeagueReviewGuideClient.Input(
              "삼성전자", "HOLD", "매출 성장 기대", "매출 성장", "", null, null, true);

      assertThat(agent.find(input, 2L, saved)).hasSize(1);
      var first = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
      assertThat(first.path("store").asBoolean()).isFalse();
      assertThat(first.path("tools").toString()).contains("searchPublishedNews");
      assertThat(first.toString()).doesNotContain("memberId", "unitPrice", "weeklyReturnRate");
      var second = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
      assertThat(second.path("input").toString()).contains("NEWS:1");
      verify(service).search(2L, "삼성전자", saved, "매출");
    }
  }

  @Test
  void toolKeepsTheServerScopeAndCapsSearchesAndEvidence() {
    var service = mock(LeagueReviewEvidenceService.class);
    Instant saved = Instant.parse("2026-10-20T00:00:00Z");
    given(service.search(2L, "삼성전자", saved, "매출")).willReturn(candidates(1, 5));
    given(service.search(2L, "삼성전자", saved, "비용")).willReturn(candidates(6, 10));
    var tool = new LeagueReviewSearchAgent.SearchTool(service, 2L, "삼성전자", saved);

    assertThat(tool.searchPublishedNews("매출")).contains("NEWS:1");
    assertThat(tool.searchPublishedNews("비용")).doesNotContain("NEWS:6");
    assertThat(tool.searchPublishedNews("세번째")).isEqualTo("Search limit reached");
    assertThat(tool.candidates()).hasSize(5);
    verify(service).search(2L, "삼성전자", saved, "매출");
    verify(service).search(2L, "삼성전자", saved, "비용");
    verifyNoMoreInteractions(service);
  }

  @Test
  void failedPlannerLogsOnlyItsTypeAndFallsBackWithoutExposingInputOrResponse() throws Exception {
    var service = mock(LeagueReviewEvidenceService.class);
    Instant saved = Instant.parse("2026-10-20T00:00:00Z");
    given(service.search(2L, "삼성전자", saved, "")).willReturn(candidates(1, 1));
    var logger = (Logger) LoggerFactory.getLogger(LeagueReviewSearchAgent.class);
    var originalLevel = logger.getLevel();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setResponseCode(401)
              .addHeader("Content-Type", "application/json")
              .setBody("{\"error\":{\"message\":\"sensitive-provider-response\"}}"));
      var agent =
          new LeagueReviewSearchAgent(
              service, server.url("/").toString(), "test-key", "test-model");
      var input =
          new OpenAiLeagueReviewGuideClient.Input(
              "삼성전자", "HOLD", "sensitive-decision-reason", "", "", null, null, false);

      assertThat(agent.find(input, 2L, saved)).hasSize(1);
      verify(service).search(2L, "삼성전자", saved, "");
      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key)
                    .containsExactly("event", "errorType");
                assertThat(event.getKeyValuePairs().get(0).value)
                    .isEqualTo("league.review-guide.planner-failed");
                assertThat(event.getKeyValuePairs().get(1).value)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                    .doesNotContain(
                        "sensitive-decision-reason", "sensitive-provider-response", "test-key");
              });
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }
  }

  private List<Candidate> candidates(long first, long last) {
    return LongStream.rangeClosed(first, last)
        .mapToObj(
            id ->
                new Candidate(
                    new ReviewGuideEvidence(
                        "NEWS:" + id,
                        "기사",
                        Instant.parse("2026-10-21T00:00:00Z"),
                        "본문",
                        "/news/" + id),
                    "본문"))
        .toList();
  }
}
