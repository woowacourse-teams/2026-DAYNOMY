package org.grit.daynomy.portfolio.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class PortfolioAnalysisControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PortfolioAnalysisService portfolioAnalysisService;

  @Test
  @DisplayName("요청으로 전달한 포트폴리오의 뉴스 분석 결과를 반환한다")
  void analyzePortfolioReturnsAnalysis() throws Exception {
    PortfolioAnalysisRequest request =
        new PortfolioAnalysisRequest(List.of(new PortfolioAssetRequest(10L, new BigDecimal("30"))));
    PortfolioAssetImpactResponse impact =
        new PortfolioAssetImpactResponse(
            10L,
            "삼성전자",
            "STOCK",
            "005930",
            new BigDecimal("30"),
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            "주가가 상승할 수 있습니다.",
            "반도체 수요 증가가 예상됩니다.",
            1);
    when(portfolioAnalysisService.analyze(1L, request))
        .thenReturn(PortfolioAnalysisResponse.of(1, List.of(impact)));

    mockMvc
        .perform(
            post("/api/news/1/portfolio-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assets":[{"assetId":10,"weight":30}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAssetCount").value(1))
        .andExpect(jsonPath("$.analyzedAssetCount").value(1))
        .andExpect(jsonPath("$.impacts[0].assetId").value(10))
        .andExpect(jsonPath("$.impacts[0].assetName").value("삼성전자"))
        .andExpect(jsonPath("$.impacts[0].category").value("STOCK"))
        .andExpect(jsonPath("$.impacts[0].assetCode").value("005930"))
        .andExpect(jsonPath("$.impacts[0].weight").value(30))
        .andExpect(jsonPath("$.impacts[0].direction").value("POSITIVE"))
        .andExpect(jsonPath("$.impacts[0].impactLevel").value("HIGH"))
        .andExpect(jsonPath("$.impacts[0].summary").value("주가가 상승할 수 있습니다."))
        .andExpect(jsonPath("$.impacts[0].reason").value("반도체 수요 증가가 예상됩니다."))
        .andExpect(jsonPath("$.impacts[0].rank").value(1));

    verify(portfolioAnalysisService).analyze(1L, request);
  }

  @Test
  @DisplayName("뉴스 ID가 숫자가 아니면 요청을 거부한다")
  void analyzePortfolioRejectsInvalidNewsIdType() throws Exception {
    mockMvc
        .perform(
            post("/api/news/not-a-number/portfolio-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assets":[]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("newsId"));

    verifyNoInteractions(portfolioAnalysisService);
  }

  @Test
  @DisplayName("뉴스가 없으면 에러 응답을 반환한다")
  void analyzePortfolioReturnsNotFoundWhenNewsIsMissing() throws Exception {
    PortfolioAnalysisRequest request = new PortfolioAnalysisRequest(List.of());
    when(portfolioAnalysisService.analyze(999L, request))
        .thenThrow(new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/news/999/portfolio-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assets":[]}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 뉴스를 찾을 수 없습니다."));

    verify(portfolioAnalysisService).analyze(999L, request);
  }
}
