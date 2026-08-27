package org.grit.daynomy.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.InvalidTokenException;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TokenService tokenService;

  @MockitoBean private TokenCookieManager tokenCookieManager;

  @Test
  @DisplayName("Google 로그인 요청을 OAuth2 인증 경로로 리다이렉트한다")
  void googleLoginRedirectsToOAuth2Authorization() throws Exception {
    mockMvc
        .perform(get("/api/auth/google"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/oauth2/authorization/google"));
  }

  @Test
  @DisplayName("Refresh Token이 없으면 재발급 요청을 거부한다")
  void refreshRejectsMissingRefreshToken() throws Exception {
    when(tokenCookieManager.getRefreshToken(any(HttpServletRequest.class))).thenReturn(null);

    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REQUIRED"))
        .andExpect(jsonPath("$.message").value("Refresh Token이 필요합니다."));

    verifyNoInteractions(tokenService);
  }

  @Test
  @DisplayName("유효한 Refresh Token이면 토큰 쿠키를 재발급한다")
  void refreshReissuesTokenCookies() throws Exception {
    TokenPair tokenPair = createTokenPair();
    when(tokenCookieManager.getRefreshToken(any(HttpServletRequest.class)))
        .thenReturn("refresh-token");
    when(tokenService.reissue("refresh-token")).thenReturn(tokenPair);

    mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isNoContent());

    verify(tokenCookieManager)
        .addTokenCookies(any(HttpServletResponse.class), any(TokenPair.class));
  }

  @Test
  @DisplayName("유효하지 않은 Refresh Token이면 인증 쿠키를 제거하고 에러를 반환한다")
  void refreshClearsCookiesWhenRefreshTokenIsInvalid() throws Exception {
    when(tokenCookieManager.getRefreshToken(any(HttpServletRequest.class)))
        .thenReturn("invalid-refresh-token");
    when(tokenService.reissue("invalid-refresh-token")).thenThrow(new InvalidTokenException());

    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
        .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));

    verify(tokenCookieManager).clearTokenCookies(any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("로그아웃하면 Refresh Token을 무효화하고 인증 쿠키를 제거한다")
  void logoutInvalidatesRefreshTokenAndClearsCookies() throws Exception {
    when(tokenCookieManager.getRefreshToken(any(HttpServletRequest.class)))
        .thenReturn("refresh-token");

    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

    verify(tokenService).logout("refresh-token");
    verify(tokenCookieManager).clearTokenCookies(any(HttpServletResponse.class));
  }

  private TokenPair createTokenPair() {
    return new TokenPair(
        "access-token",
        "refresh-token",
        Instant.parse("2026-08-24T01:00:00Z"),
        Instant.parse("2026-09-07T00:00:00Z"));
  }
}
