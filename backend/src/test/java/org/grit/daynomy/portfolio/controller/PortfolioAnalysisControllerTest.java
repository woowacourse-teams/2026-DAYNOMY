package org.grit.daynomy.portfolio.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = PortfolioAnalysisController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class PortfolioAnalysisControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PortfolioAnalysisService portfolioAnalysisService;

  @BeforeEach
  void setUpSecurityContext() {
    AuthenticatedMember member = new AuthenticatedMember(3L, MemberRole.USER);
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(member, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("인증 회원의 뉴스 포트폴리오 분석 결과를 반환한다")
  void getPortfolioAnalysisReturnsAnalysisForAuthenticatedMember() throws Exception {
    PortfolioAssetImpactResponse impact =
        new PortfolioAssetImpactResponse(
            101L,
            10L,
            "삼성전자",
            "STOCK",
            "005930",
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            "주가가 상승할 수 있습니다.",
            "반도체 수요 증가가 예상됩니다.",
            1);
    when(portfolioAnalysisService.getPortfolioAnalysis(3L, 1L))
        .thenReturn(new PortfolioAnalysisResponse(List.of(impact)));

    mockMvc
        .perform(get("/api/news/1/portfolio-analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.impacts[0].bookmarkId").value(101))
        .andExpect(jsonPath("$.impacts[0].assetId").value(10))
        .andExpect(jsonPath("$.impacts[0].name").value("삼성전자"))
        .andExpect(jsonPath("$.impacts[0].category").value("STOCK"))
        .andExpect(jsonPath("$.impacts[0].assetCode").value("005930"))
        .andExpect(jsonPath("$.impacts[0].direction").value("POSITIVE"))
        .andExpect(jsonPath("$.impacts[0].impactLevel").value("HIGH"))
        .andExpect(jsonPath("$.impacts[0].expectedReaction").value("주가가 상승할 수 있습니다."))
        .andExpect(jsonPath("$.impacts[0].reason").value("반도체 수요 증가가 예상됩니다."))
        .andExpect(jsonPath("$.impacts[0].sortOrder").value(1));

    verify(portfolioAnalysisService).getPortfolioAnalysis(3L, 1L);
  }

  @Test
  @DisplayName("뉴스 ID가 숫자가 아니면 요청을 거부한다")
  void getPortfolioAnalysisRejectsInvalidNewsIdType() throws Exception {
    mockMvc
        .perform(get("/api/news/not-a-number/portfolio-analysis"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("newsId"));

    verifyNoInteractions(portfolioAnalysisService);
  }

  @Test
  @DisplayName("뉴스가 없으면 에러 응답을 반환한다")
  void getPortfolioAnalysisReturnsNotFoundWhenNewsIsMissing() throws Exception {
    when(portfolioAnalysisService.getPortfolioAnalysis(3L, 999L))
        .thenThrow(new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    mockMvc
        .perform(get("/api/news/999/portfolio-analysis"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 뉴스를 찾을 수 없습니다."));

    verify(portfolioAnalysisService).getPortfolioAnalysis(3L, 999L);
  }
}
