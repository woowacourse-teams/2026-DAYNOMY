package org.grit.daynomy.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.handler.RestAccessDeniedHandler;
import org.grit.daynomy.auth.handler.RestAuthenticationEntryPoint;
import org.grit.daynomy.auth.oauth.CustomOidcUserService;
import org.grit.daynomy.auth.oauth.OAuth2LoginSuccessHandler;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.auth.token.TokenCookieManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {
  private final CustomOidcUserService customOidcUserService;
  private final OAuth2LoginSuccessHandler successHandler;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final CorsConfigurationSource corsConfigurationSource;
  private final TokenCookieManager tokenCookieManager;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri)
      throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    String[] publicReadPaths = {
      "/api/auth/google",
      "/api/auth/csrf",
      "/api/portfolio/analysis/tasks",
      "/api/portfolio/analysis/tasks/*",
      "/api/news",
      "/api/news/today",
      "/api/news/*",
      "/api/news/*/keywords",
      "/api/news/*/market-analysis",
      "/api/search/news",
      "/api/search/contents",
      "/api/stocks",
      "/api/stocks/*/price",
      "/api/assets/*/contents",
      "/api/contents",
      "/api/contents/assets",
      "/api/content-rankings",
      "/api/exchange-rates/usd-krw",
      "/api/people",
      "/api/league/weeks",
      "/api/league/rankings",
      "/api/league/investors/*",
      "/api/league/investors/*/details",
      "/api/league/investors/*/daily-history",
      "/swagger-ui.html",
      "/swagger-ui/**",
      "/v3/api-docs",
      "/v3/api-docs/**",
      "/v3/api-docs.yaml",
      "/v3/api-docs.yaml/**",
      "/login",
      "/actuator",
      "/actuator/health",
      "/actuator/health/**",
      "/actuator/prometheus"
    };

    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .securityContext(
            context ->
                context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        request ->
                            ("/api/portfolio/analysis".equals(request.getServletPath())
                                    || "/api/portfolio/analysis/tasks"
                                        .equals(request.getServletPath())
                                    || request
                                        .getServletPath()
                                        .matches("/api/portfolio/analysis/tasks/[^/]+/retry"))
                                && tokenCookieManager.getAccessToken(request) == null)
                    .csrfTokenRequestHandler(csrfHandler))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/oauth2/authorization/*", "/login/oauth2/code/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, publicReadPaths)
                    .permitAll()
                    .requestMatchers(HttpMethod.HEAD, publicReadPaths)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/feedback",
                        "/api/content-interactions",
                        "/api/investment-calendar",
                        "/api/portfolio/analysis",
                        "/api/portfolio/calculate")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/portfolio/analysis/tasks",
                        "/api/portfolio/analysis/tasks/*/retry")
                    .anonymous()
                    .requestMatchers("/api/portfolio", "/api/portfolio/**")
                    .authenticated()
                    .requestMatchers("/api/users/**", "/api/v1/users/**", "/api/v2/users/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo -> userInfo.oidcUserService(customOidcUserService::loadUser))
                    .successHandler(successHandler)
                    .failureHandler(
                        (request, response, exception) ->
                            response.sendRedirect(failureRedirectUri)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
