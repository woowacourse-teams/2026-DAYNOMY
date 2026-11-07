package org.grit.daynomy.pmfsurvey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.pmfsurvey.service.PmfSurveyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class PmfAnalysisRequestTracking {

  @Bean
  MappedInterceptor pmfAnalysisRequestInterceptor(
      PmfSurveyIdentity identity, PmfSurveyService service) {
    String[] paths = {
      "/api/portfolio/analysis/tasks",
      "/api/portfolio/analysis/tasks/*/retry",
      "/api/users/me/portfolio/analysis/tasks",
      "/api/users/me/portfolio/analysis/tasks/refresh"
    };
    return new MappedInterceptor(
        paths,
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!"POST".equals(request.getMethod()) || identity.hasInvalidToken(request)) {
              return true;
            }
            AuthenticatedMember member =
                request.getUserPrincipal() instanceof Authentication authentication
                        && authentication.getPrincipal() instanceof AuthenticatedMember principal
                    ? principal
                    : null;
            if (!identity.excluded(member, request)) {
              try {
                service.recordAnalysisRequest(
                    identity.getOrCreate(member, request, response),
                    identity.visitSession(request));
              } catch (DataAccessException exception) {
                log.warn("PMF 분석 요청 기록 실패: {}", exception.getClass().getSimpleName());
              }
            }
            return true;
          }
        });
  }
}
