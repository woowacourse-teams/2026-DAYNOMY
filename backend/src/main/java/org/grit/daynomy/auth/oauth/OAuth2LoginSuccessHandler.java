package org.grit.daynomy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final String successRedirectUri;

  public OAuth2LoginSuccessHandler(
      @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
    this.successRedirectUri = successRedirectUri;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    response.sendRedirect(successRedirectUri);
  }
}
