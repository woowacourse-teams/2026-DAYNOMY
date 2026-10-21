package org.grit.daynomy.content.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnonymousContentVisitorCookieManagerTest {

  private final AnonymousContentVisitorCookieManager manager =
      new AnonymousContentVisitorCookieManager("test-secret", true);

  @Test
  void issuesAndVerifiesSignedVisitorCookie() {
    MockHttpServletResponse firstResponse = new MockHttpServletResponse();

    UUID issued = manager.getOrCreate(new MockHttpServletRequest(), firstResponse);

    String token = cookieValue(firstResponse.getHeader("Set-Cookie"));
    MockHttpServletRequest nextRequest = new MockHttpServletRequest();
    nextRequest.setCookies(new Cookie(AnonymousContentVisitorCookieManager.COOKIE_NAME, token));
    MockHttpServletResponse nextResponse = new MockHttpServletResponse();

    assertThat(manager.getOrCreate(nextRequest, nextResponse)).isEqualTo(issued);
    assertThat(nextResponse.getHeader("Set-Cookie")).isNull();
    assertThat(firstResponse.getHeader("Set-Cookie"))
        .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
  }

  @Test
  void replacesTamperedVisitorCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String tamperedToken = UUID.randomUUID() + ".invalid";
    request.setCookies(
        new Cookie(AnonymousContentVisitorCookieManager.COOKIE_NAME, tamperedToken));
    MockHttpServletResponse response = new MockHttpServletResponse();

    UUID visitorId = manager.getOrCreate(request, response);

    assertThat(response.getHeader("Set-Cookie")).contains(visitorId.toString());
  }

  private String cookieValue(String setCookie) {
    return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
  }
}
