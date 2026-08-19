package org.grit.daynomy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
  private final String failureRedirectUri;

  public OAuth2LoginFailureHandler(
      @Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri) {
    this.failureRedirectUri = failureRedirectUri;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {

    response.sendRedirect(failureRedirectUri);
  }
}
