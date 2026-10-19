package org.grit.daynomy.emailsubscription.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
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
  }

  @LocalServerPort private int port;
  @Autowired private MemberRepository members;
  @Autowired private EmailSubscriptionRepository subscriptions;
  @Autowired private JwtTokenProvider tokens;

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
