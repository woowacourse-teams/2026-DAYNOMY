package org.grit.daynomy.auth.v1.token;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.grit.daynomy.auth.token.TokenPair;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TokenCookieManagerV1Test {

  private final TokenCookieManagerV1 manager = new TokenCookieManagerV1(true);

  @Test
  void issuesRefreshCookieForV1Path() {
    var response = new MockHttpServletResponse();
    manager.addTokenCookies(
        response,
        new TokenPair(
            "access", "refresh", Instant.now().plusSeconds(600), Instant.now().plusSeconds(3600)));

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .contains(
                        "refresh_token=refresh",
                        "Path=/api/v1/auth",
                        "Secure",
                        "HttpOnly",
                        "SameSite=Lax"));
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("refresh_token", "refresh"));
    assertThat(manager.getRefreshToken(request)).isEqualTo("refresh");
  }

  @Test
  void clearsCookiesWithTheirOriginalPaths() {
    var response = new MockHttpServletResponse();
    manager.clearTokenCookies(response);

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(2)
        .anySatisfy(
            cookie ->
                assertThat(cookie).contains("refresh_token=", "Path=/api/v1/auth", "Max-Age=0"))
        .anySatisfy(cookie -> assertThat(cookie).contains("access_token=", "Path=/;", "Max-Age=0"));
  }
}
