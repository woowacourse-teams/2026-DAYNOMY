package org.grit.daynomy.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.exception.AuthErrorCode;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.ErrorCode;
import org.grit.daynomy.common.response.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    ErrorCode errorCode = getErrorCode(request);

    response.setStatus(errorCode.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
  }

  private ErrorCode getErrorCode(HttpServletRequest request) {
    if (Boolean.TRUE.equals(
        request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))) {
      return AuthErrorCode.INVALID_TOKEN;
    }

    return AuthErrorCode.UNAUTHORIZED;
  }
}
