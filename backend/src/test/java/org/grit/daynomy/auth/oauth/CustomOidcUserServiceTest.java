package org.grit.daynomy.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.auth.token.TokenPair;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class CustomOidcUserServiceTest {
  private final MemberService members = mock(MemberService.class);
  private final CustomOidcUserService service = new CustomOidcUserService(members);

  @Test
  void googleLoginIssuesTokensForTheLocalMemberAndClearsTheLoginSession() throws Exception {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(42L);
    given(member.getRole()).willReturn(MemberRole.USER);
    given(
            members.findOrCreateGoogleMember(
                "google-subject", "user@example.invalid", "Google 이름", null))
        .willReturn(member);
    var principal = service.loadUser(request("google-subject", "user@example.invalid"));
    assertThat(principal.getSubject()).isEqualTo("google-subject");
    assertThat(principal.getName()).isEqualTo("42");
    assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    TokenService tokens = mock(TokenService.class);
    TokenCookieManager cookies = mock(TokenCookieManager.class);
    TokenPair pair =
        new TokenPair("access", "refresh", Instant.now(), Instant.now().plusSeconds(60));
    given(tokens.issue(42L)).willReturn(pair);
    var handler = new OAuth2LoginSuccessHandler(tokens, cookies, "/login-complete");
    var httpRequest = new MockHttpServletRequest();
    var httpResponse = new MockHttpServletResponse();
    var session = (MockHttpSession) httpRequest.getSession();
    var authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      handler.onAuthenticationSuccess(httpRequest, httpResponse, authentication);
      then(cookies).should().addTokenCookies(httpResponse, pair);
      assertThat(session.isInvalid()).isTrue();
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(httpResponse.getRedirectedUrl()).isEqualTo("/login-complete");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void missingEmailDoesNotCreateAMember(String email) {
    assertThatThrownBy(() -> service.loadUser(request("google-subject", email)))
        .isInstanceOf(OAuth2AuthenticationException.class);
    then(members).shouldHaveNoInteractions();
  }

  @Test
  void blankSubjectDoesNotCreateAMember() {
    assertThatThrownBy(() -> service.loadUser(request(" ", "user@example.invalid")))
        .isInstanceOf(OAuth2AuthenticationException.class);
    then(members).shouldHaveNoInteractions();
  }

  private OidcUserRequest request(String subject, String email) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("google")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/google")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .scope("openid", "email", "profile")
            .build();
    Map<String, Object> claims = new HashMap<>(Map.of("sub", subject, "name", "Google 이름"));
    if (email != null) claims.put("email", email);
    Instant now = Instant.now();
    return new OidcUserRequest(
        registration,
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "access", now, now.plusSeconds(60)),
        new OidcIdToken("id-token", now, now.plusSeconds(60), claims));
  }
}
