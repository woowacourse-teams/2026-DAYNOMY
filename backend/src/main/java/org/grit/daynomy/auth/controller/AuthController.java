package org.grit.daynomy.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.exception.AuthErrorCode;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Google OAuth 로그인 및 토큰 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@RestController
public class AuthController {

  private final TokenService tokenService;
  private final TokenCookieManager tokenCookieManager;

  @Operation(summary = "Google 로그인", description = "Google OAuth 로그인 페이지로 이동합니다.")
  @GetMapping("/google")
  public ResponseEntity<Void> googleLogin() {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/oauth2/authorization/google"))
        .build();
  }

  @Operation(summary = "CSRF 토큰 조회", description = "인증이 필요한 변경 요청에 사용할 CSRF 토큰을 조회합니다.")
  @GetMapping("/csrf")
  public CsrfToken csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
    return csrfToken;
  }

  @Operation(summary = "Access Token 재발급", description = "Refresh Token을 검증하고 인증 토큰을 재발급합니다.")
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(
      @Parameter(hidden = true) HttpServletRequest request,
      @Parameter(hidden = true) HttpServletResponse response) {

    String refreshToken = tokenCookieManager.getRefreshToken(request);

    if (refreshToken == null) {
      throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REQUIRED);
    }

    try {
      TokenPair tokenPair = tokenService.reissue(refreshToken);
      tokenCookieManager.addTokenCookies(response, tokenPair);

      return ResponseEntity.noContent().build();
    } catch (InvalidTokenException exception) {
      tokenCookieManager.clearTokenCookies(response);
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
  }

  @Operation(summary = "로그아웃", description = "Refresh Token을 무효화하고 인증 쿠키를 삭제합니다.")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @Parameter(hidden = true) HttpServletRequest request,
      @Parameter(hidden = true) HttpServletResponse response) {

    String refreshToken = tokenCookieManager.getRefreshToken(request);

    tokenService.logout(refreshToken);
    tokenCookieManager.clearTokenCookies(response);

    return ResponseEntity.noContent().build();
  }
}
