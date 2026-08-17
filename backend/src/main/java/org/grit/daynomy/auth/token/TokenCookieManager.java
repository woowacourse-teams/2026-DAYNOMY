package org.grit.daynomy.auth.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class TokenCookieManager {

  public static final String ACCESS_TOKEN_COOKIE = "access_token";
  public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

  private final boolean secure;

  public TokenCookieManager(@Value("${cookie.secure}") boolean secure) {
    this.secure = secure;
  }

  public void addTokenCookies(HttpServletResponse response, TokenPair tokenPair) {
    addCookie(
        response,
        ACCESS_TOKEN_COOKIE,
        tokenPair.accessToken(),
        "/",
        tokenPair.accessTokenExpiresAt());

    addCookie(
        response,
        REFRESH_TOKEN_COOKIE,
        tokenPair.refreshToken(),
        "/api/auth",
        tokenPair.refreshTokenExpiresAt());
  }

  public String getAccessToken(HttpServletRequest request) {
    return findCookie(request, ACCESS_TOKEN_COOKIE);
  }

  public String getRefreshToken(HttpServletRequest request) {
    return findCookie(request, REFRESH_TOKEN_COOKIE);
  }

  public void clearTokenCookies(HttpServletResponse response) {
    clearCookie(response, ACCESS_TOKEN_COOKIE, "/");
    clearCookie(response, REFRESH_TOKEN_COOKIE, "/api/auth");
  }

  private void addCookie(
      HttpServletResponse response, String name, String value, String path, Instant expiresAt) {

    Duration maxAge = Duration.between(Instant.now(), expiresAt);

    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearCookie(HttpServletResponse response, String name, String path) {
    ResponseCookie cookie =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(Duration.ZERO)
            .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private String findCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();

    if (cookies == null) {
      return null;
    }

    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }

    return null;
  }
}
