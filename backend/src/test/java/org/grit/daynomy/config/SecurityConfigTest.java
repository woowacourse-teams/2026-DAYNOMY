package org.grit.daynomy.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.grit.daynomy.auth.handler.RestAccessDeniedHandler;
import org.grit.daynomy.auth.handler.RestAuthenticationEntryPoint;
import org.grit.daynomy.auth.oauth.CustomOidcUserService;
import org.grit.daynomy.auth.oauth.OAuth2LoginSuccessHandler;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@WebMvcTest(SecurityConfigTest.ProbeController.class)
@Import({
  SecurityConfig.class,
  CorsConfig.class,
  JwtAuthenticationFilter.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  SecurityConfigTest.WebSecurityConfiguration.class,
  SecurityConfigTest.ProbeController.class
})
class SecurityConfigTest {
  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSecurity
  static class WebSecurityConfiguration {}

  @Autowired private MockMvc mvc;
  @MockitoBean private CustomOidcUserService oidcUsers;
  @MockitoBean private OAuth2LoginSuccessHandler loginSuccess;
  @MockitoBean private JwtTokenProvider tokens;
  @MockitoBean private MemberRepository members;
  @MockitoBean private TokenCookieManager cookies;
  @MockitoBean private ClientRegistrationRepository registrations;

  @BeforeEach
  void googleRegistration() {
    given(registrations.findByRegistrationId("google"))
        .willReturn(
            CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .build());
  }

  static Stream<String> publicReads() {
    return Stream.of(
        "/api/auth/google",
        "/api/auth/csrf",
        "/api/portfolio/analysis/tasks",
        "/api/portfolio/analysis/tasks/1",
        "/api/news",
        "/api/news/today",
        "/api/news/1",
        "/api/news/1/keywords",
        "/api/news/1/market-analysis",
        "/api/search/news",
        "/api/search/contents",
        "/api/stocks",
        "/api/stocks/1/price",
        "/api/assets/1/contents",
        "/api/contents",
        "/api/contents/assets",
        "/api/content-rankings",
        "/api/exchange-rates/usd-krw",
        "/api/people",
        "/api/league/weeks",
        "/api/league/rankings",
        "/api/league/investors/1",
        "/api/league/investors/1/details",
        "/api/league/investors/1/daily-history",
        "/swagger-ui.html",
        "/swagger-ui/index.html",
        "/swagger-ui/swagger-ui.css",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/v3/api-docs.yaml",
        "/v3/api-docs.yaml/public",
        "/actuator",
        "/actuator/health",
        "/actuator/health/readiness",
        "/actuator/health/liveness",
        "/actuator/prometheus");
  }

  @ParameterizedTest
  @MethodSource("publicReads")
  void publicReadsAndHeadRemainAccessible(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(request(HttpMethod.HEAD, path)).andExpect(status().isOk());
    mvc.perform(get(path).with(user("member"))).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/feedback",
        "/api/content-interactions",
        "/api/investment-calendar",
        "/api/portfolio/analysis",
        "/api/portfolio/calculate"
      })
  void publicPostsRemainAccessibleWithCsrf(String path) throws Exception {
    mvc.perform(request(HttpMethod.POST, path).with(csrf())).andExpect(status().isOk());
    mvc.perform(request(HttpMethod.POST, path).with(csrf()).with(user("member")))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/portfolio/analysis/tasks", "/api/portfolio/analysis/tasks/1/retry"})
  void guestTaskWritesRemainAnonymousOnly(String path) throws Exception {
    mvc.perform(request(HttpMethod.POST, path).with(csrf())).andExpect(status().isOk());
    mvc.perform(request(HttpMethod.POST, path).with(csrf()).with(user("member")))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /api/unclassified-private",
    "POST, /api/unclassified-private",
    "GET, /api/news/1/internal",
    "POST, /api/news"
  })
  void unlistedHandlerAndUnlistedMethodAreDeniedEvenWhenRegistered(String method, String path)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path).with(csrf()))
        .andExpect(status().isUnauthorized());
    for (String role : new String[] {"USER", "ADMIN"}) {
      mvc.perform(
              request(HttpMethod.valueOf(method), path)
                  .with(csrf())
                  .with(user("member").roles(role)))
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/users/me/probe",
        "/api/v1/users/me/probe",
        "/api/v2/users/me/probe",
        "/api/portfolio/probe"
      })
  void memberNamespacesStillRequireAuthentication(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).with(user("member"))).andExpect(status().isOk());
  }

  @Test
  void adminNamespaceStillRequiresAdminRole() throws Exception {
    mvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/probe").with(user("member"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/probe").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void oauthAndCorsRemainAvailable() throws Exception {
    mvc.perform(get("/oauth2/authorization/google"))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));
    mvc.perform(
            options("/api/feedback")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  // 실제 필터 체인을 통과시켜 업무 입력 검증과 구분해 허용·거부를 확인한다.
  @RestController
  static class ProbeController {
    @GetMapping({
      "/api/auth/google",
      "/api/auth/csrf",
      "/api/portfolio/analysis/tasks",
      "/api/portfolio/analysis/tasks/{id}",
      "/api/news",
      "/api/news/today",
      "/api/news/{id}",
      "/api/news/{id}/keywords",
      "/api/news/{id}/market-analysis",
      "/api/search/news",
      "/api/search/contents",
      "/api/stocks",
      "/api/stocks/{id}/price",
      "/api/assets/{id}/contents",
      "/api/contents",
      "/api/contents/assets",
      "/api/content-rankings",
      "/api/exchange-rates/usd-krw",
      "/api/people",
      "/api/league/weeks",
      "/api/league/rankings",
      "/api/league/investors/{id}",
      "/api/league/investors/{id}/details",
      "/api/league/investors/{id}/daily-history",
      "/swagger-ui.html",
      "/swagger-ui/**",
      "/v3/api-docs",
      "/v3/api-docs/**",
      "/v3/api-docs.yaml",
      "/v3/api-docs.yaml/**",
      "/actuator",
      "/actuator/health",
      "/actuator/health/{group}",
      "/actuator/prometheus",
      "/api/users/me/probe",
      "/api/v1/users/me/probe",
      "/api/v2/users/me/probe",
      "/api/portfolio/probe",
      "/api/admin/probe"
    })
    String read() {
      return "allowed";
    }

    @PostMapping({
      "/api/auth/refresh",
      "/api/auth/logout",
      "/api/feedback",
      "/api/content-interactions",
      "/api/investment-calendar",
      "/api/portfolio/analysis",
      "/api/portfolio/calculate",
      "/api/portfolio/analysis/tasks",
      "/api/portfolio/analysis/tasks/{id}/retry",
      "/api/news"
    })
    String write() {
      return "allowed";
    }

    @RequestMapping(
        path = {"/api/unclassified-private", "/api/news/{id}/internal"},
        method = {RequestMethod.GET, RequestMethod.POST})
    String unlisted() {
      return "must be denied";
    }
  }
}
