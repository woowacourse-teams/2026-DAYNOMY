package org.grit.daynomy.config;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.oauth.CustomOAuth2UserService;
import org.grit.daynomy.auth.oauth.OAuth2LoginFailureHandler;
import org.grit.daynomy.auth.oauth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {
  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2LoginSuccessHandler successHandler;
  private final OAuth2LoginFailureHandler failureHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        "/api/auth/**", "/login/oauth2/**", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/users/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .authorizationEndpoint(authorization -> authorization.baseUri("/api/auth"))
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(successHandler)
                    .failureHandler(failureHandler));

    return http.build();
  }
}
