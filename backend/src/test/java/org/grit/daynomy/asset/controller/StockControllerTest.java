package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.dto.StockPriceResponse;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.asset.dto.StockSearchResponse;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.asset.service.StockSearchService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = StockController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StockControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private StockSearchService stockSearchService;
  @MockitoBean private StockPriceService stockPriceService;

  @Test
  @DisplayName("국내 주식 검색 API는 종목 검색 결과를 반환한다")
  void searchStocks() throws Exception {
    given(stockSearchService.search("삼성"))
        .willReturn(
            new StockSearchResponse(
                List.of(new StockSearchItemResponse(1L, "005930", "삼성전자", StockMarket.KOSPI))));

    mockMvc
        .perform(get("/api/stocks").param("q", "삼성"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stocks[0].assetId").value(1))
        .andExpect(jsonPath("$.stocks[0].assetCode").value("005930"))
        .andExpect(jsonPath("$.stocks[0].name").value("삼성전자"))
        .andExpect(jsonPath("$.stocks[0].market").value("KOSPI"));

    then(stockSearchService).should().search("삼성");
  }

  @Test
  @DisplayName("국내 주식 검색 API는 검색어가 없으면 요청을 거부한다")
  void searchStocksRejectsMissingKeyword() throws Exception {
    mockMvc
        .perform(get("/api/stocks"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("검색어를 입력해주세요."));

    verifyNoInteractions(stockSearchService, stockPriceService);
  }

  @Test
  @DisplayName("국내 주식 최근 종가 API는 가장 최근 거래일의 종가를 반환한다")
  void getLatestStockPrice() throws Exception {
    given(stockPriceService.getLatestPrice(1L))
        .willReturn(
            new StockPriceResponse(
                1L, "005930", "삼성전자", LocalDate.of(2026, 9, 18), new BigDecimal("82000.00")));

    mockMvc
        .perform(get("/api/stocks/{assetId}/price", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetId").value(1))
        .andExpect(jsonPath("$.assetCode").value("005930"))
        .andExpect(jsonPath("$.name").value("삼성전자"))
        .andExpect(jsonPath("$.baseDate").value("2026-09-18"))
        .andExpect(jsonPath("$.closePrice").value(82000.00));

    then(stockPriceService).should().getLatestPrice(1L);
  }
}
