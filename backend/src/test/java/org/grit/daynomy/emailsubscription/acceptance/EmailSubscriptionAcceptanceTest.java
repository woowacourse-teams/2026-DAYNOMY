package org.grit.daynomy.emailsubscription.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification.SourceCheck;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.emailsubscription.service.CommonStockAnalysisService;
import org.grit.daynomy.emailsubscription.service.EmailUnsubscribeService;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailSubscriptionAcceptanceTest {

  private static final String PATH = "/api/users/me/email-subscription";

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    registry.add("email.delivery.unsubscribe-secret", () -> "test-only-secret-at-least-32-bytes");
    registry.add("email.delivery.public-base-url", () -> "http://localhost:8080");
  }

  @LocalServerPort private int port;
  @Autowired private MemberRepository members;
  @Autowired private EmailSubscriptionRepository subscriptions;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private EmailUnsubscribeService unsubscribe;
  @Autowired private AssetRepository assets;
  @Autowired private CommonStockAnalysisService analyses;

  @Test
  void onlyAdminCanInvalidateAnalysisAndInvalidationIsIdempotent() {
    var member = member();
    var path = "/api/admin/email-analyses/";
    var asset =
        assets.save(
            Asset.listedStock(
                "종목",
                UUID.randomUUID().toString(),
                StockMarket.KOSPI,
                null,
                LocalDate.of(2026, 10, 26)));
    var id =
        analyses.storePending(
            new CommonStockAnalysisKey(asset.getId(), "model", "v1", "e1", Instant.EPOCH),
            new CommonStockAnalysisContent(null, null, "분석", "", "", "근거", List.of()));
    client(member)
        .body("{\"reason\":\"정정\"}")
        .post(path + id + "/invalidate")
        .then()
        .statusCode(403);
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .body("{\"reason\":\"정정\"}")
        .post(path + id + "/invalidate")
        .then()
        .statusCode(401);
    admin(member).body("{\"reason\":\" \"}").post(path + id + "/invalidate").then().statusCode(400);
    admin(member)
        .body("{\"reason\":\"공시 정정\"}")
        .post(path + id + "/invalidate")
        .then()
        .statusCode(200)
        .body("analysisId", equalTo(id.intValue()))
        .body("cancelledDeliveries", equalTo(0));
    admin(member)
        .body("{\"reason\":\"다른 사유\"}")
        .post(path + id + "/invalidate")
        .then()
        .statusCode(200);
    admin(member)
        .get(path + id + "/correction-review-deliveries")
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
    admin(member).get(path + id + "/correction-review-deliveries?size=101").then().statusCode(400);
    admin(member)
        .body("{\"reason\":\"정정\"}")
        .post(path + Long.MAX_VALUE + "/invalidate")
        .then()
        .statusCode(404)
        .body("code", equalTo("EMAIL_ANALYSIS_NOT_FOUND"));
  }

  @Test
  void adminCanBrowseReadAndInvalidateAnalysisWhileOtherUsersCannotReadIt() {
    var member = member();
    var asset =
        assets.save(
            Asset.listedStock(
                "관리자 조회 종목",
                UUID.randomUUID().toString(),
                StockMarket.KOSPI,
                null,
                LocalDate.of(2026, 10, 27)));
    var source =
        new Source(
            "공식 실적",
            "https://example.invalid/releases/earnings-20261026",
            LocalDate.of(2026, 10, 26),
            LocalDate.of(2026, 10, 26),
            "매출이 증가했어요.",
            "Revenue increased.");
    var content =
        new CommonStockAnalysisContent(
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            "실적 개선",
            "반응",
            "전망",
            "매출 증가",
            List.of(source));
    var id =
        analyses.storePending(
            new CommonStockAnalysisKey(
                asset.getId(), "model", "admin-v1", "e1", Instant.parse("2026-10-26T22:00:00Z")),
            content);
    var path = "/api/admin/email-analyses";
    given().port(port).get(path).then().statusCode(401);
    given().port(port).get(path + "/" + id).then().statusCode(401);
    client(member).get(path).then().statusCode(403);
    client(member).get(path + "/" + id).then().statusCode(403);
    admin(member)
        .queryParam("assetId", asset.getId())
        .queryParam("status", "PENDING")
        .queryParam("size", 1)
        .get(path)
        .then()
        .statusCode(200)
        .body("items[0].id", equalTo(id.intValue()))
        .body("items[0].assetName", equalTo("관리자 조회 종목"))
        .body("totalElements", equalTo(1))
        .body("page", equalTo(0));
    admin(member)
        .get(path + "/" + id)
        .then()
        .statusCode(200)
        .body("analysis.id", equalTo(id.intValue()))
        .body("content.reason", equalTo("매출 증가"))
        .body(
            "content.sources[0].url", equalTo("https://example.invalid/releases/earnings-20261026"))
        .body("content.sources[0].factQuote", equalTo("Revenue increased."))
        .body("verification", nullValue())
        .body("invalidationReason", nullValue());
    var stored =
        analyses
            .findStored(
                new CommonStockAnalysisKey(
                    asset.getId(),
                    "model",
                    "admin-v1",
                    "e1",
                    Instant.parse("2026-10-26T22:00:00Z")))
            .orElseThrow();
    analyses.recordVerification(
        id,
        new CommonStockAnalysisVerification(
            stored.getContentHash(),
            Instant.now(),
            List.of(new SourceCheck(source.url(), "b".repeat(64), true, true, true, true))));
    admin(member)
        .get(path + "/" + id)
        .then()
        .statusCode(200)
        .body("analysis.status", equalTo("VERIFIED"))
        .body("verification.sources[0].companyMatched", equalTo(true))
        .body("verification.sources[0].passed", equalTo(true));
    admin(member)
        .body("{\"reason\":\"공시 정정\"}")
        .post(path + "/" + id + "/invalidate")
        .then()
        .statusCode(200);
    admin(member)
        .get(path + "/" + id)
        .then()
        .statusCode(200)
        .body("analysis.status", equalTo("INVALIDATED"))
        .body("invalidationReason", equalTo("공시 정정"))
        .body("content.reason", equalTo("매출 증가"));
    admin(member)
        .queryParam("assetId", asset.getId())
        .queryParam("status", "PENDING")
        .get(path)
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(0));
    admin(member)
        .get(path + "/" + Long.MAX_VALUE)
        .then()
        .statusCode(404)
        .body("code", equalTo("EMAIL_ANALYSIS_NOT_FOUND"));
  }

  private RequestSpecification admin(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.ADMIN).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }

  @Test
  void publicLinkRequiresExplicitPostAndOldLinksCannotUndoResubscription() {
    Member member = member();
    client(member).put(PATH).then().statusCode(200);
    var start = subscriptions.findById(member.getId()).orElseThrow().getSubscribedAt();
    var link = unsubscribe.link(member.getId(), start);
    var token = link.substring(link.indexOf("?token=") + 7);
    given()
        .port(port)
        .queryParam("token", token)
        .get(EmailUnsubscribeService.PATH)
        .then()
        .statusCode(200)
        .header("Referrer-Policy", "no-referrer");
    assertThat(subscriptions.findById(member.getId()).orElseThrow().getStatus())
        .isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    given()
        .port(port)
        .queryParam("token", token + "tampered")
        .post(EmailUnsubscribeService.PATH)
        .then()
        .statusCode(400);
    assertThat(subscriptions.findById(member.getId()).orElseThrow().getStatus())
        .isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    given()
        .port(port)
        .formParam("token", token)
        .post(EmailUnsubscribeService.PATH)
        .then()
        .statusCode(200);
    given()
        .port(port)
        .queryParam("token", token)
        .formParam("List-Unsubscribe", "One-Click")
        .post(EmailUnsubscribeService.PATH)
        .then()
        .statusCode(200);
    assertThat(subscriptions.findById(member.getId()).orElseThrow().getStatus())
        .isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);
    client(member).put(PATH).then().statusCode(200);
    given()
        .port(port)
        .queryParam("token", token)
        .post(EmailUnsubscribeService.PATH)
        .then()
        .statusCode(400);
    assertThat(subscriptions.findById(member.getId()).orElseThrow().getStatus())
        .isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
  }

  @Test
  void invitationSubscriptionAndResubscriptionArePersistentAndAccountScoped() {
    Member member = member();
    client(member)
        .get(PATH)
        .then()
        .statusCode(200)
        .body("status", equalTo("UNDECIDED"))
        .body("email", equalTo(member.getEmail()))
        .body("promptRequired", equalTo(true))
        .body("subscribedAt", nullValue());
    assertThat(subscriptions.findById(member.getId())).isEmpty();

    client(member)
        .post(PATH + "/dismiss")
        .then()
        .statusCode(200)
        .body("status", equalTo("DECLINED"))
        .body("promptRequired", equalTo(false));

    String firstStart =
        client(member)
            .body("{\"email\":\"different@example.invalid\",\"memberId\":999999}")
            .put(PATH)
            .then()
            .statusCode(200)
            .body("status", equalTo("SUBSCRIBED"))
            .body("email", equalTo(member.getEmail()))
            .extract()
            .jsonPath()
            .getString("subscribedAt");
    client(member).put(PATH).then().statusCode(200).body("subscribedAt", equalTo(firstStart));
    client(member)
        .post(PATH + "/dismiss")
        .then()
        .statusCode(200)
        .body("status", equalTo("SUBSCRIBED"));

    client(member)
        .delete(PATH)
        .then()
        .statusCode(200)
        .body("status", equalTo("UNSUBSCRIBED"))
        .body("promptRequired", equalTo(false));
    client(member)
        .post(PATH + "/dismiss")
        .then()
        .statusCode(200)
        .body("status", equalTo("UNSUBSCRIBED"));
    String secondStart =
        client(member)
            .put(PATH)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("subscribedAt");
    assertThat(Instant.parse(secondStart)).isAfter(Instant.parse(firstStart));
    client(member)
        .get(PATH)
        .then()
        .statusCode(200)
        .body("status", equalTo("SUBSCRIBED"))
        .body("subscribedAt", equalTo(secondStart));

    Member other = member();
    client(other)
        .get(PATH)
        .then()
        .statusCode(200)
        .body("status", equalTo("UNDECIDED"))
        .body("email", equalTo(other.getEmail()));
    client(other).delete(PATH).then().statusCode(200).body("status", equalTo("UNDECIDED"));
    assertThat(subscriptions.findById(other.getId())).isEmpty();
    client(member).get(PATH).then().statusCode(200).body("status", equalTo("SUBSCRIBED"));
  }

  @Test
  void unauthenticatedAndMissingCsrfRequestsCannotChangeSubscriptions() {
    given().port(port).get(PATH).then().statusCode(401);
    Member member = member();
    String token = tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken();

    given().port(port).cookie("access_token", token).put(PATH).then().statusCode(403);
    given().port(port).cookie("access_token", token).post(PATH + "/dismiss").then().statusCode(403);
    given().port(port).cookie("access_token", token).delete(PATH).then().statusCode(403);

    assertThat(subscriptions.findById(member.getId())).isEmpty();
  }

  @Test
  void withdrawnMembersCannotReadOrChangeSubscriptionState() {
    Member member = member();
    client(member).put(PATH).then().statusCode(200);
    member.withdraw();
    members.saveAndFlush(member);

    client(member).get(PATH).then().statusCode(401);
    client(member).put(PATH).then().statusCode(401);
  }

  @Test
  void simultaneousFirstSubscriptionsCreateOneRecordWithOneStartTime() throws Exception {
    Member member = member();
    List<RequestSpecification> clients = List.of(client(member), client(member));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var requests =
          clients.stream()
              .map(
                  request ->
                      executor.submit(
                          () -> {
                            if (!start.await(10, TimeUnit.SECONDS)) {
                              throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                            }
                            return request
                                .put(PATH)
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath()
                                .getString("subscribedAt");
                          }))
              .toList();
      start.countDown();
      String firstStart = requests.getFirst().get(15, TimeUnit.SECONDS);
      assertThat(requests.getLast().get(15, TimeUnit.SECONDS)).isEqualTo(firstStart);
      var saved = subscriptions.findById(member.getId()).orElseThrow();
      assertThat(saved.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
      assertThat(saved.getSubscribedAt()).isEqualTo(Instant.parse(firstStart));
    }
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private RequestSpecification client(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
