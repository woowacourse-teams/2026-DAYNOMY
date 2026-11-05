package org.grit.daynomy.pmfsurvey.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
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
  @Autowired AssetRepository assets;
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
  void existingMemberIsReturningAndResponseFollowsTheAccountWithoutGuestCookies() {
    Member existing = createMember();
    jdbc.update(
        "UPDATE members SET created_at = now() - interval '2 days' WHERE id = ?", existing.getId());
    var firstDevice = Map.of("access_token", tokens.issue(existing.getId()).accessToken());
    request()
        .cookies(firstDevice)
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("isFirstVisit", equalTo(false))
        .body("responseId", nullValue());
    var submitted = write(firstDevice).body(Map.of("answer", "VERY_DISAPPOINTED")).post(RESPONSES);
    submitted.then().statusCode(201);
    var secondDevice = Map.of("access_token", tokens.issue(existing.getId()).accessToken());
    request()
        .cookies(secondDevice)
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("responseId", equalTo(submitted.jsonPath().getInt("id")));
  }

  @Test
  void firstObservedMemberCanBeReturningBasedOnExistingPortfolioUse() {
    Member existing = createMember();
    String code = UUID.randomUUID().toString();
    long assetId =
        assets
            .save(
                Asset.listedStock(
                    "PMF 검증 종목",
                    code,
                    StockMarket.KOSPI,
                    "KR" + code.replace("-", "").substring(0, 10),
                    LocalDate.now()))
            .getId();
    Long portfolioId =
        jdbc.queryForObject(
            "INSERT INTO portfolios (member_id, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
            Long.class,
            existing.getId());
    jdbc.update(
        """
        INSERT INTO portfolio_holdings
            (portfolio_id, asset_id, quantity, average_purchase_price, created_at, updated_at)
        VALUES (?, ?, 1, 100, now() - interval '1 second', now() - interval '1 second')
        """,
        portfolioId,
        assetId);

    var returning = Map.of("access_token", tokens.issue(existing.getId()).accessToken());
    request()
        .cookies(returning)
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("isFirstVisit", equalTo(false));

    var newMember = Map.of("access_token", member(false));
    request()
        .cookies(newMember)
        .get(CURRENT)
        .then()
        .statusCode(200)
        .body("isFirstVisit", equalTo(true));
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
    Member member = createMember();
    if (administrator) {
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", member.getId());
    }
    return tokens.issue(member.getId()).accessToken();
  }

  private Member createMember() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
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
