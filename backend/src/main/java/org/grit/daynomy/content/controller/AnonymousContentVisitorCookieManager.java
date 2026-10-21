package org.grit.daynomy.content.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AnonymousContentVisitorCookieManager {

  static final String COOKIE_NAME = "content_visitor";
  private static final Duration MAX_AGE = Duration.ofDays(365);
  private static final String ALGORITHM = "HmacSHA256";

  private final byte[] secret;
  private final boolean secure;

  public AnonymousContentVisitorCookieManager(
      @Value("${content-interaction.anonymous-token-secret}") String secret,
      @Value("${cookie.secure}") boolean secure) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.secure = secure;
  }

  public UUID getOrCreate(HttpServletRequest request, HttpServletResponse response) {
    UUID visitorId = verifiedVisitorId(findCookie(request));
    if (visitorId != null) {
      return visitorId;
    }

    visitorId = UUID.randomUUID();
    addCookie(response, visitorId);
    return visitorId;
  }

  private UUID verifiedVisitorId(String token) {
    if (token == null) {
      return null;
    }

    int separator = token.indexOf('.');
    if (separator <= 0 || separator == token.length() - 1) {
      return null;
    }

    String value = token.substring(0, separator);
    try {
      byte[] providedSignature = Base64.getUrlDecoder().decode(token.substring(separator + 1));
      if (!MessageDigest.isEqual(signature(value), providedSignature)) {
        return null;
      }
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private void addCookie(HttpServletResponse response, UUID visitorId) {
    String value = visitorId.toString();
    String token =
        value + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature(value));
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(MAX_AGE)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private String findCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private byte[] signature(String value) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return mac.doFinal(("content-visitor:" + value).getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new IllegalStateException("Anonymous visitor signature is unavailable", exception);
    }
  }
}
