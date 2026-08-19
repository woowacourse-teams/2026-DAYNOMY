package org.grit.daynomy.member.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.grit.daynomy.auth.repository.RefreshTokenRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private MemberRepository memberRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private TokenService tokenService;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAll();
    memberRepository.deleteAll();
  }

  @Test
  @DisplayName("회원 조회 API는 성공 시 회원 DTO를 직접 반환한다")
  void getMeReturnsMemberResponse() throws Exception {
    Member member =
        memberRepository.save(
            Member.createGoogleMember("google-id", "member@example.com", "daynomy", null));
    TokenPair tokenPair = tokenService.issue(member.getId());

    HttpResponse<String> response = getMe(tokenPair.accessToken());
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/id").asLong()).isEqualTo(member.getId());
    assertThat(body.at("/email").asText()).isEqualTo("member@example.com");
    assertThat(body.at("/nickname").asText()).isEqualTo("daynomy");
    assertThat(body.has("status")).isFalse();
    assertThat(body.has("body")).isFalse();
  }

  @Test
  @DisplayName("회원 조회 API는 인증 정보가 없으면 공통 에러 응답을 반환한다")
  void getMeWithoutAuthenticationReturnsUnauthorized() throws Exception {
    HttpResponse<String> response = getMe(null);
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(body.at("/code").asText()).isEqualTo("UNAUTHORIZED");
    assertThat(body.at("/message").asText()).isEqualTo("로그인이 필요합니다.");
    assertThat(body.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("회원 조회 API는 잘못된 JWT에 공통 에러 응답을 반환한다")
  void getMeWithInvalidTokenReturnsInvalidToken() throws Exception {
    HttpResponse<String> response = getMe("invalid-token");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(body.at("/code").asText()).isEqualTo("INVALID_TOKEN");
    assertThat(body.at("/message").asText()).isEqualTo("유효하지 않은 인증 정보입니다.");
    assertThat(body.size()).isEqualTo(2);
  }

  private HttpResponse<String> getMe(String accessToken) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me")).GET();

    if (accessToken != null) {
      request.header("Cookie", "access_token=" + accessToken);
    }

    return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
