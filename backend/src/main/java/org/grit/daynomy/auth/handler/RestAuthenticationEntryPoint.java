package org.grit.daynomy.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
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
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ApiResponse<Void> body = createErrorResponse(request);
    objectMapper.writeValue(response.getWriter(), body);
  }

  private ApiResponse<Void> createErrorResponse(HttpServletRequest request) {
    if (Boolean.TRUE.equals(
        request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))) {
      return ApiResponse.error("INVALID_TOKEN", "유효하지 않은 인증 정보입니다.");
    }

    return ApiResponse.error("UNAUTHORIZED", "로그인이 필요합니다.");
  }
}
