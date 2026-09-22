package org.grit.daynomy.portfolio.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.PortfolioMarketAllocationResponse;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = PortfolioController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PortfolioControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PortfolioCalculationService calculationService;

  @Test
  @DisplayName("포트폴리오 계산 API는 평가 결과와 차트용 비중을 반환한다")
  void calculatePortfolio() throws Exception {
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000"))));
    given(calculationService.calculate(request)).willReturn(response());

    mockMvc
        .perform(
            post("/api/portfolio/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "holdings": [{
                        "assetId": 1,
                        "quantity": 10,
                        "averagePurchasePrice": 65000
                      }]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-09-18"))
        .andExpect(jsonPath("$.totalPurchaseAmount").value(650000.00))
        .andExpect(jsonPath("$.totalEvaluationAmount").value(820000.00))
        .andExpect(jsonPath("$.totalProfitLoss").value(170000.00))
        .andExpect(jsonPath("$.totalReturnRate").value(26.15))
        .andExpect(jsonPath("$.holdings[0].weight").value(100.00))
        .andExpect(jsonPath("$.marketAllocations[0].market").value("KOSPI"));

    then(calculationService).should().calculate(request);
  }

  @Test
  @DisplayName("포트폴리오 계산 API는 빈 보유자산 요청을 거부한다")
  void rejectEmptyHoldings() throws Exception {
    mockMvc
        .perform(
            post("/api/portfolio/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdings\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("holdings"))
        .andExpect(jsonPath("$.errors[0].reason").value("보유자산을 한 개 이상 입력해주세요."));

    verifyNoInteractions(calculationService);
  }

  @Test
  @DisplayName("포트폴리오 계산 API는 0 이하인 수량과 평균 매수가를 거부한다")
  void rejectInvalidHoldingValues() throws Exception {
    mockMvc
        .perform(
            post("/api/portfolio/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "holdings": [{
                        "assetId": 1,
                        "quantity": 0,
                        "averagePurchasePrice": 0
                      }]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors.length()").value(2));

    verifyNoInteractions(calculationService);
  }

  @Test
  @DisplayName("포트폴리오 계산 API는 비어 있는 보유자산 항목을 거부한다")
  void rejectNullHolding() throws Exception {
    mockMvc
        .perform(
            post("/api/portfolio/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdings\": [null]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verifyNoInteractions(calculationService);
  }

  private PortfolioCalculationResponse response() {
    return new PortfolioCalculationResponse(
        LocalDate.of(2026, 9, 18),
        new BigDecimal("650000.00"),
        new BigDecimal("820000.00"),
        new BigDecimal("170000.00"),
        new BigDecimal("26.15"),
        List.of(
            new PortfolioHoldingResponse(
                1L,
                "005930",
                "삼성전자",
                StockMarket.KOSPI,
                LocalDate.of(2026, 9, 18),
                10L,
                new BigDecimal("65000.00"),
                new BigDecimal("82000.00"),
                new BigDecimal("650000.00"),
                new BigDecimal("820000.00"),
                new BigDecimal("170000.00"),
                new BigDecimal("26.15"),
                new BigDecimal("100.00"))),
        List.of(
            new PortfolioMarketAllocationResponse(
                StockMarket.KOSPI, new BigDecimal("820000.00"), new BigDecimal("100.00"))));
  }
}
