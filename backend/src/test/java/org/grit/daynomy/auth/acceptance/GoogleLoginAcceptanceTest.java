package org.grit.daynomy.auth.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GoogleLoginAcceptanceTest.OAuthConfiguration.class)
class GoogleLoginAcceptanceTest extends SharedDatabaseTest {
  private static final MockWebServer GOOGLE = startGoogle();
  private static final RSAKey KEY = key();
  private static final AtomicReference<String> NONCE = new AtomicReference<>();
  private static final AtomicReference<String> SUBJECT = new AtomicReference<>();
  private static final AtomicReference<String> TOKEN_FAILURE = new AtomicReference<>();
  private Map<String, String> authorizationCookies;
  private String authorizationState;
  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired RefreshTokenRepository refreshTokens;

  @AfterAll
  static void closeGoogle() throws Exception {
    GOOGLE.shutdown();
  }

  @Test
  void callbackCreatesMemberIssuesCookiesAndExistingMemberCanLogInAgain() {
    SUBJECT.set("google-" + UUID.randomUUID());
    TOKEN_FAILURE.set(null);
    long initialMembers = members.count();
    var first = login();
    assertThat(first.statusCode()).isEqualTo(302);
    assertThat(first.header("Location")).isEqualTo("http://localhost:5173");
    assertThat(first.cookie("access_token")).isNotBlank();
    assertThat(first.cookie("refresh_token")).isNotBlank();
    assertThat(first.headers().getValues("Set-Cookie"))
        .anySatisfy(
            cookie ->
                assertThat(cookie).contains("access_token=", "Path=/;", "HttpOnly", "SameSite=Lax"))
        .anySatisfy(
            cookie -> assertThat(cookie).contains("refresh_token=", "Path=/api/auth", "HttpOnly"));
    assertThat(members.count()).isEqualTo(initialMembers + 1);
    var profile =
        given()
            .port(port)
            .cookie("access_token", first.cookie("access_token"))
            .get("/api/users/me");
    profile.then().statusCode(200).body("email", equalTo(SUBJECT.get() + "@example.invalid"));
    long memberId = profile.jsonPath().getLong("id");
    assertThat(refreshTokens.findAll())
        .anySatisfy(token -> assertThat(token.getMember().getId()).isEqualTo(memberId));
    var second = login();
    assertThat(second.cookie("access_token")).isNotBlank();
    assertThat(members.count()).isEqualTo(initialMembers + 1);
    given()
        .port(port)
        .cookie("access_token", second.cookie("access_token"))
        .get("/api/users/me")
        .then()
        .statusCode(200)
        .body("id", equalTo((int) memberId));
    // Replay the consumed authorization request with its original session cookie and state.
    int requestsBeforeReplay = GOOGLE.getRequestCount();
    long tokensBeforeReplay = refreshTokens.count();
    given()
        .port(port)
        .redirects()
        .follow(false)
        .cookies(authorizationCookies)
        .queryParam("code", "test-code")
        .queryParam("state", authorizationState)
        .get("/login/oauth2/code/google")
        .then()
        .statusCode(302)
        .header("Location", equalTo("http://localhost:5173/login?error=oauth"));
    assertThat(GOOGLE.getRequestCount()).isEqualTo(requestsBeforeReplay);
    assertThat(refreshTokens.count()).isEqualTo(tokensBeforeReplay);
  }

  @Test
  void providerFailureDoesNotPersistMemberOrIssueCookies() {
    SUBJECT.set("failed-" + UUID.randomUUID());
    TOKEN_FAILURE.set("http-error");
    long initialMembers = members.count();
    long initialTokens = refreshTokens.count();
    try {
      var response = login();
      assertThat(response.statusCode()).isEqualTo(302);
      assertThat(response.header("Location")).isEqualTo("http://localhost:5173/login?error=oauth");
      assertThat(response.cookie("access_token")).isNull();
      assertThat(response.cookie("refresh_token")).isNull();
      assertThat(members.count()).isEqualTo(initialMembers);
      assertThat(refreshTokens.count()).isEqualTo(initialTokens);
    } finally {
      TOKEN_FAILURE.set(null);
    }
  }

  @Test
  void mismatchedStateIsRejectedBeforeCallingTheProvider() {
    var authorization = authorize();
    int requests = GOOGLE.getRequestCount();
    var response =
        given()
            .port(port)
            .redirects()
            .follow(false)
            .cookies(authorization.getCookies())
            .queryParam("code", "code")
            .queryParam("state", "wrong-state")
            .get("/login/oauth2/code/google");
    assertThat(response.header("Location")).isEqualTo("http://localhost:5173/login?error=oauth");
    assertThat(response.cookie("access_token")).isNull();
    assertThat(GOOGLE.getRequestCount()).isEqualTo(requests);
  }

  private io.restassured.response.Response authorize() {
    given()
        .port(port)
        .redirects()
        .follow(false)
        .get("/api/auth/google")
        .then()
        .statusCode(302)
        .header("Location", equalTo("/oauth2/authorization/google"));
    return given().port(port).redirects().follow(false).get("/oauth2/authorization/google");
  }

  private io.restassured.response.Response login() {
    var authorization = authorize();
    assertThat(authorization.statusCode()).isEqualTo(302);
    Map<String, String> query =
        Arrays.stream(URI.create(authorization.header("Location")).getRawQuery().split("&"))
            .map(value -> value.split("=", 2))
            .collect(
                Collectors.toMap(
                    pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    NONCE.set(query.get("nonce"));
    authorizationCookies = authorization.getCookies();
    authorizationState = query.get("state");
    return given()
        .port(port)
        .redirects()
        .follow(false)
        .cookies(authorization.getCookies())
        .queryParam("code", "test-code")
        .queryParam("state", query.get("state"))
        .get("/login/oauth2/code/google");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OAuthConfiguration {
    @Bean
    @Primary
    ClientRegistrationRepository localGoogleRegistration() {
      String base = GOOGLE.url("/").toString();
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("google")
              .clientId("test-client")
              .clientSecret("test-secret")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri(base + "authorize")
              .tokenUri(base + "token")
              .jwkSetUri(base + "jwks")
              .userInfoUri(base + "userinfo")
              .userNameAttributeName("sub")
              .issuerUri(base)
              .build());
    }
  }

  private static MockWebServer startGoogle() {
    try {
      var server = new MockWebServer();
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
              try {
                return switch (request.getRequestUrl().encodedPath()) {
                  case "/token" ->
                      TOKEN_FAILURE.get() != null
                          ? json(400, "{\"error\":\"invalid_grant\"}")
                          : json(
                              200,
                              new ObjectMapper()
                                  .writeValueAsString(
                                      Map.of(
                                          "access_token",
                                          "mock-google-token",
                                          "token_type",
                                          "Bearer",
                                          "expires_in",
                                          3600,
                                          "scope",
                                          "openid profile email",
                                          "id_token",
                                          idToken())));
                  case "/jwks" ->
                      json(200, "{\"keys\":[" + KEY.toPublicJWK().toJSONString() + "]}");
                  case "/userinfo" ->
                      json(
                          200,
                          new ObjectMapper()
                              .writeValueAsString(
                                  Map.of(
                                      "sub",
                                      SUBJECT.get(),
                                      "email",
                                      SUBJECT.get() + "@example.invalid",
                                      "email_verified",
                                      true,
                                      "name",
                                      "Google 사용자")));
                  default -> new MockResponse().setResponseCode(404);
                };
              } catch (Exception exception) {
                return new MockResponse().setResponseCode(500);
              }
            }
          });
      server.start();
      return server;
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).keyID("local-google-key").generate();
    } catch (JOSEException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static String idToken() throws JOSEException {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(GOOGLE.url("/").toString())
            .subject(SUBJECT.get())
            .audience("test-client")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .claim("nonce", NONCE.get())
            .claim("email", SUBJECT.get() + "@example.invalid")
            .claim("name", "Google 사용자")
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(KEY));
    return jwt.serialize();
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
