package org.grit.daynomy.market.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.market.dto.MarketAnalysisResponse;
import org.grit.daynomy.market.exception.MarketErrorCode;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = MarketAnalysisController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MarketAnalysisControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MarketAnalysisService marketAnalysisService;

  @Test
  @DisplayName("뉴스 ID에 해당하는 시장 분석 결과를 반환한다")
  void getMarketAnalysisReturnsAnalysis() throws Exception {
    when(marketAnalysisService.getMarketAnalysis(1L)).thenReturn(createResponse());

    mockMvc
        .perform(get("/api/news/1/market-analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.observedFact").value("기준금리가 인하됐습니다."))
        .andExpect(jsonPath("$.interpretation").value("위험자산 선호가 높아질 수 있습니다."))
        .andExpect(jsonPath("$.risk").value("물가가 다시 상승하면 해석이 달라질 수 있습니다."))
        .andExpect(jsonPath("$.watchPoint").value("다음 소비자물가 발표를 확인해야 합니다."))
        .andExpect(jsonPath("$.limitation").value("자산별 가격 데이터는 포함하지 않았습니다."));

    verify(marketAnalysisService).getMarketAnalysis(1L);
  }

  @Test
  @DisplayName("시장 분석이 없으면 에러 응답을 반환한다")
  void getMarketAnalysisReturnsNotFoundWhenAnalysisIsMissing() throws Exception {
    when(marketAnalysisService.getMarketAnalysis(999L))
        .thenThrow(new BusinessException(MarketErrorCode.MARKET_ANALYSIS_NOT_FOUND));

    mockMvc
        .perform(get("/api/news/999/market-analysis"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MARKET_ANALYSIS_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 뉴스의 시장 분석을 찾을 수 없습니다."));

    verify(marketAnalysisService).getMarketAnalysis(999L);
  }

  @Test
  @DisplayName("뉴스 ID가 숫자가 아니면 요청을 거부한다")
  void getMarketAnalysisRejectsInvalidNewsIdType() throws Exception {
    mockMvc
        .perform(get("/api/news/not-a-number/market-analysis"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("newsId"));

    verifyNoInteractions(marketAnalysisService);
  }

  private MarketAnalysisResponse createResponse() {
    return new MarketAnalysisResponse(
        "기준금리가 인하됐습니다.",
        "위험자산 선호가 높아질 수 있습니다.",
        "물가가 다시 상승하면 해석이 달라질 수 있습니다.",
        "다음 소비자물가 발표를 확인해야 합니다.",
        "자산별 가격 데이터는 포함하지 않았습니다.");
  }
}
