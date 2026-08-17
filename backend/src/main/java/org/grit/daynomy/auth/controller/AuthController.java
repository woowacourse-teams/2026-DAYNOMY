package org.grit.daynomy.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.auth.token.TokenPair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/auth")
@RestController
public class AuthController {

  private final TokenService tokenService;
  private final TokenCookieManager tokenCookieManager;

  @GetMapping("/google")
  public ResponseEntity<Void> googleLogin() {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/oauth2/authorization/google"))
        .build();
  }

  @GetMapping("/csrf")
  public CsrfToken csrf(CsrfToken csrfToken) {
    return csrfToken;
  }

  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {

    String refreshToken = tokenCookieManager.getRefreshToken(request);

    if (refreshToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      TokenPair tokenPair = tokenService.reissue(refreshToken);
      tokenCookieManager.addTokenCookies(response, tokenPair);

      return ResponseEntity.noContent().build();
    } catch (InvalidTokenException exception) {
      tokenCookieManager.clearTokenCookies(response);

      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {

    String refreshToken = tokenCookieManager.getRefreshToken(request);

    tokenService.logout(refreshToken);
    tokenCookieManager.clearTokenCookies(response);

    return ResponseEntity.noContent().build();
  }
}
