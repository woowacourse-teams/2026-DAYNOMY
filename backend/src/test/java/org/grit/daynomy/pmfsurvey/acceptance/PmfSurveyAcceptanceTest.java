package org.grit.daynomy.pmfsurvey.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "pmf-survey.active-id=42")
class PmfSurveyAcceptanceTest {

  private static final String CURRENT = "/api/pmf-surveys/current";
  private static final String RESPONSES = "/api/pmf-surveys/42/responses";
  private static final String SNOOZE = "/api/pmf-surveys/42/snooze";
  private static final String RESULTS = "/api/admin/pmf-surveys/42/results";

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DB::getJdbcUrl);
    properties.add("spring.datasource.username", DB::getUsername);
    properties.add("spring.datasource.password", DB::getPassword);
    properties.add("spring.datasource.driver-class-name", DB::getDriverClassName);
  }

  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired TokenService tokens;
  @Autowired JdbcTemplate jdbc;

  @Test
  void guestCanReturnAfterSessionExpiryAndSnoozeAcrossSessions() {
    var first = request().get(CURRENT);
    first
        .then()
        .statusCode(200)
        .body("isFirstVisit", equalTo(true))
        .body("responseId", nullValue());
    var cookies = first.cookies();
    request().cookies(cookies).get(CURRENT).then().body("isFirstVisit", equalTo(true));

    var guest = Map.of("content_visitor", cookies.get("content_visitor"));
    var returning = request().cookies(guest).get(CURRENT);
    returning.then().statusCode(200).body("isFirstVisit", equalTo(false));

    write(guest).post(SNOOZE).then().statusCode(204);
    write(guest).post(SNOOZE).then().statusCode(204);
    var hidden = request().cookies(guest).get(CURRENT);
    hidden.then().statusCode(200).body("responseId", nullValue());
    var expectedMidnight =
        OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toInstant();
    assertThat(OffsetDateTime.parse(hidden.jsonPath().getString("hiddenUntil")).toInstant())
        .isEqualTo(expectedMidnight);
    request().get(CURRENT).then().body("hiddenUntil", nullValue());
  }

  @Test
  void duplicateAndConcurrentSubmissionKeepsOneResponseAndItsOriginalReason() throws Exception {
    var guest = request().get(CURRENT).cookies();
    var first =
        write(guest).body(Map.of("answer", "VERY_DISAPPOINTED", "reason", " 이유 ")).post(RESPONSES);
    first.then().statusCode(201);
    long id = first.jsonPath().getLong("id");
    write(guest)
        .body(Map.of("answer", "NOT_DISAPPOINTED"))
        .post(RESPONSES)
        .then()
        .statusCode(200)
        .body("id", equalTo((int) id));
    assertThat(
            jdbc.queryForMap(
                "SELECT answer, reason, cohort, source FROM pmf_survey_responses WHERE id = ?", id))
        .containsEntry("answer", "VERY_DISAPPOINTED")
        .containsEntry("reason", "이유")
        .containsEntry("cohort", "HISTORY_UNKNOWN")
        .containsEntry("source", "IN_APP");
    request().cookies(guest).get(CURRENT).then().body("responseId", equalTo((int) id));

    var anotherGuest = request().get(CURRENT).cookies();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one =
          executor.submit(
              () ->
                  write(anotherGuest)
                      .body(Map.of("answer", "SOMEWHAT_DISAPPOINTED"))
                      .post(RESPONSES));
      var two =
          executor.submit(
              () -> write(anotherGuest).body(Map.of("answer", "NOT_DISAPPOINTED")).post(RESPONSES));
      var oneResult = one.get(15, TimeUnit.SECONDS);
      var twoResult = two.get(15, TimeUnit.SECONDS);
      assertThat(oneResult.statusCode()).isIn(200, 201);
      assertThat(twoResult.statusCode()).isIn(200, 201).isNotEqualTo(oneResult.statusCode());
      assertThat(oneResult.jsonPath().getLong("id")).isEqualTo(twoResult.jsonPath().getLong("id"));
    }
  }

  @Test
  void manualAnalysisRequestCountsEvenWhenAnalysisDoesNotComplete() {
    var analysisSession = request().get("/api/portfolio/analysis/tasks");
    analysisSession.then().statusCode(200);
    var beforeClick = request().get(CURRENT).cookies();
    var failed =
        write(analysisSession.cookies())
            .post("/api/portfolio/analysis/tasks/" + UUID.randomUUID() + "/retry");
    failed.then().statusCode(404);

    var guest = Map.of("content_visitor", failed.cookie("content_visitor"));
    request()
        .cookies(guest)
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("isFirstVisit", equalTo(false));
    var active = write(guest).body(Map.of("answer", "VERY_DISAPPOINTED")).post(RESPONSES);
    var other = write(beforeClick).body(Map.of("answer", "VERY_DISAPPOINTED")).post(RESPONSES);
    active.then().statusCode(201);
    other.then().statusCode(201);
    assertThat(cohort(active.jsonPath().getLong("id"))).isEqualTo("OFFICIAL_PMF");
    assertThat(cohort(other.jsonPath().getLong("id"))).isEqualTo("HISTORY_UNKNOWN");
  }

  @Test
  void onlyAdministratorsCanReadResultsAndStaffCannotParticipate() {
    var admin = Map.of("access_token", member(true));
    var member = Map.of("access_token", member(false));
    request().cookies(member).get(CURRENT).then().statusCode(200).body("eligible", equalTo(true));
    request().cookies(member).get(RESULTS).then().statusCode(403);
    request().get(RESULTS).then().statusCode(401);
    request()
        .cookies(admin)
        .get(RESULTS)
        .then()
        .statusCode(200)
        .body("surveyId", equalTo(42))
        .body("groups.size()", equalTo(3))
        .body("groups[0].answers.VERY_DISAPPOINTED", notNullValue());
    request().cookies(admin).get(CURRENT).then().statusCode(200).body("eligible", equalTo(false));
    request()
        .header("Host", "staff.daynomy.com")
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("eligible", equalTo(false));
    write(admin).post(SNOOZE).then().statusCode(403);
  }

  @Test
  void requiresCsrfAndRejectsInvalidInputOrMissingGuestIdentity() {
    var guest = request().get(CURRENT).cookies();
    request()
        .cookies(guest)
        .body(Map.of("answer", "VERY_DISAPPOINTED"))
        .post(RESPONSES)
        .then()
        .statusCode(403);
    write(guest).body(Map.of("answer", 0)).post(RESPONSES).then().statusCode(400);
    write(guest)
        .body(Map.of("answer", "VERY_DISAPPOINTED", "reason", "x".repeat(501)))
        .post(RESPONSES)
        .then()
        .statusCode(400);
    write(Map.of())
        .body(Map.of("answer", "VERY_DISAPPOINTED"))
        .post(RESPONSES)
        .then()
        .statusCode(409);
    write(guest).post("/api/pmf-surveys/43/snooze").then().statusCode(404);
  }

  private String cohort(long responseId) {
    return jdbc.queryForObject(
        "SELECT cohort FROM pmf_survey_responses WHERE id = ?", String.class, responseId);
  }

  private String member(boolean administrator) {
    String key = UUID.randomUUID().toString();
    Member member =
        members.save(
            Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
    if (administrator) {
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", member.getId());
    }
    return tokens.issue(member.getId()).accessToken();
  }

  private RequestSpecification request() {
    return given().port(port).contentType("application/json");
  }

  private RequestSpecification write(Map<String, String> cookies) {
    var csrf = request().cookies(cookies).get("/api/auth/csrf");
    return request()
        .cookies(cookies)
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
