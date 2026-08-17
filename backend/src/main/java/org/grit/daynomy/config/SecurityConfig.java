package org.grit.daynomy.config;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.handler.RestAccessDeniedHandler;
import org.grit.daynomy.auth.handler.RestAuthenticationEntryPoint;
import org.grit.daynomy.auth.oauth.CustomOAuth2UserService;
import org.grit.daynomy.auth.oauth.CustomOidcUserService;
import org.grit.daynomy.auth.oauth.OAuth2LoginFailureHandler;
import org.grit.daynomy.auth.oauth.OAuth2LoginSuccessHandler;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {
  private final CustomOAuth2UserService customOAuth2UserService;
  private final CustomOidcUserService customOidcUserService;
  private final OAuth2LoginSuccessHandler successHandler;
  private final OAuth2LoginFailureHandler failureHandler;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

    http.securityContext(
            context ->
                context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        "/api/auth/**",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/users/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo ->
                            userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService::loadUser))
                    .successHandler(successHandler)
                    .failureHandler(failureHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
