package org.grit.daynomy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.auth.token.TokenPair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final TokenService tokenService;
  private final TokenCookieManager tokenCookieManager;
  private final String successRedirectUri;

  public OAuth2LoginSuccessHandler(
      TokenService tokenService,
      TokenCookieManager tokenCookieManager,
      @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
    this.tokenService = tokenService;
    this.tokenCookieManager = tokenCookieManager;
    this.successRedirectUri = successRedirectUri;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    MemberOAuth2Principal principal = (MemberOAuth2Principal) authentication.getPrincipal();
    TokenPair tokenPair = tokenService.issue(principal.getMemberId());

    tokenCookieManager.addTokenCookies(response, tokenPair);

    HttpSession session = request.getSession(false);

    if (session != null) {
      session.invalidate();
    }

    SecurityContextHolder.clearContext();
    response.sendRedirect(successRedirectUri);
  }
}
