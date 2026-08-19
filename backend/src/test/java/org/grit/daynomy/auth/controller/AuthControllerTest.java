package org.grit.daynomy.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Test
  @DisplayName("토큰 재발급 API는 Refresh Token이 없으면 공통 에러 응답을 반환한다")
  void refreshWithoutRefreshTokenReturnsErrorResponse() throws Exception {
    CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    HttpClient httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();

    String csrfToken = getCsrfToken(httpClient);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/refresh"))
            .header("X-XSRF-TOKEN", csrfToken)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(body.at("/code").asText()).isEqualTo("REFRESH_TOKEN_REQUIRED");
    assertThat(body.at("/message").asText()).isEqualTo("Refresh Token이 필요합니다.");
    assertThat(body.size()).isEqualTo(2);
  }

  private String getCsrfToken(HttpClient httpClient) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/csrf")).GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    return objectMapper.readTree(response.body()).at("/token").asText();
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }
}
