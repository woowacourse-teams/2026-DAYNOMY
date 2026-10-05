package org.grit.daynomy.portfolio.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisSourceResponse;
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
  @DisplayName("요청으로 전달한 전체 포트폴리오의 분석 결과를 반환한다")
  void analyzePortfolioReturnsAnalysis() throws Exception {
    PortfolioAnalysisRequest request =
        new PortfolioAnalysisRequest(
            List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"))));
    PortfolioAssetImpactResponse impact =
        new PortfolioAssetImpactResponse(
            "삼성전자",
            new BigDecimal("100"),
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            "반도체 수요가 증가했어요.",
            "주가가 상승할 수 있습니다.",
            "수요 증가가 이어질 수 있어요.",
            "반도체 수요 증가가 예상됩니다.",
            "반도체 수요가 전년 대비 증가했습니다.",
            List.of(new PortfolioAnalysisSourceResponse("한국거래소", "https://example.com/news")),
            1);
    when(portfolioAnalysisService.analyze(request))
        .thenReturn(
            PortfolioAnalysisResponse.of(
                1,
                new PortfolioImpactSummary(
                    ImpactDirection.POSITIVE,
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("0.00")),
                Instant.parse("2026-10-05T08:00:00Z"),
                "반도체 비중이 높아 긍정적인 영향이 예상돼요.",
                List.of(impact),
                List.of(new PortfolioAnalysisSourceResponse("한국거래소", "https://example.com/news"))));

    mockMvc
        .perform(
            post("/api/portfolio/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assets":[{"assetName":"삼성전자","weight":100}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAssetCount").value(1))
        .andExpect(jsonPath("$.analyzedAssetCount").value(1))
        .andExpect(jsonPath("$.overallDirection").value("POSITIVE"))
        .andExpect(jsonPath("$.overallScore").value(100.00))
        .andExpect(jsonPath("$.positiveImpactScore").value(100.00))
        .andExpect(jsonPath("$.negativeImpactScore").value(0.00))
        .andExpect(jsonPath("$.analyzedAt").value("2026-10-05T08:00:00Z"))
        .andExpect(jsonPath("$.overallImpact").value("반도체 비중이 높아 긍정적인 영향이 예상돼요."))
        .andExpect(jsonPath("$.impacts[0].assetName").value("삼성전자"))
        .andExpect(jsonPath("$.impacts[0].weight").value(100))
        .andExpect(jsonPath("$.impacts[0].direction").value("POSITIVE"))
        .andExpect(jsonPath("$.impacts[0].impactLevel").value("HIGH"))
        .andExpect(jsonPath("$.impacts[0].issueSummary").value("반도체 수요가 증가했어요."))
        .andExpect(jsonPath("$.impacts[0].expectedReaction").value("주가가 상승할 수 있습니다."))
        .andExpect(jsonPath("$.impacts[0].outlook").value("수요 증가가 이어질 수 있어요."))
        .andExpect(jsonPath("$.impacts[0].reason").value("반도체 수요 증가가 예상됩니다."))
        .andExpect(jsonPath("$.impacts[0].evidenceSentence").value("반도체 수요가 전년 대비 증가했습니다."))
        .andExpect(jsonPath("$.impacts[0].sources[0].title").value("한국거래소"))
        .andExpect(jsonPath("$.impacts[0].sources[0].url").value("https://example.com/news"))
        .andExpect(jsonPath("$.impacts[0].rank").value(1))
        .andExpect(jsonPath("$.sources[0].title").value("한국거래소"))
        .andExpect(jsonPath("$.sources[0].url").value("https://example.com/news"));

    verify(portfolioAnalysisService).analyze(request);
  }

  @Test
  @DisplayName("종목명이 100자를 초과하면 요청을 거부한다")
  void analyzePortfolioRejectsTooLongAssetName() throws Exception {
    String assetName = "가".repeat(101);

    mockMvc
        .perform(
            post("/api/portfolio/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assets":[{"assetName":"%s","weight":100}]}
                    """
                        .formatted(assetName)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("assets[0].assetName"));

    verifyNoInteractions(portfolioAnalysisService);
  }

  @Test
  @DisplayName("포트폴리오 자산이 50개를 초과하면 요청을 거부한다")
  void analyzePortfolioRejectsTooManyAssets() throws Exception {
    String assets =
        IntStream.rangeClosed(1, 51)
            .mapToObj(index -> "{\"assetName\":\"종목%d\",\"weight\":1}".formatted(index))
            .collect(Collectors.joining(","));

    mockMvc
        .perform(
            post("/api/portfolio/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assets\":[%s]}".formatted(assets)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("assets"));

    verifyNoInteractions(portfolioAnalysisService);
  }
}
