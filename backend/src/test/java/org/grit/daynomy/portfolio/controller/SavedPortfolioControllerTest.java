package org.grit.daynomy.portfolio.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioCurrentPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.SavedPortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = SavedPortfolioController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class SavedPortfolioControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SavedPortfolioService savedPortfolioService;
  @MockitoBean private PortfolioSnapshotService snapshotService;

  @BeforeEach
  void setUpAuthentication() {
    var member = new AuthenticatedMember(3L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/portfolio/holdings", "/api/users/me/portfolio/holdings"})
  void retiredHoldingCreationApiIsNotFound(String path) throws Exception {
    mockMvc
        .perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"quantity":10,"averagePurchasePrice":65000}
                    """))
        .andExpect(status().isNotFound());

    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @Test
  void transactionUsesAuthenticatedMemberAndReturnsRealizedProfitLoss() throws Exception {
    var request =
        new PortfolioTransactionRequest(
            1L,
            PortfolioTransactionType.SELL,
            LocalDate.of(2026, 10, 7),
            2L,
            new BigDecimal("80000"),
            new BigDecimal("100"),
            new BigDecimal("200"));
    given(savedPortfolioService.transact(3L, request))
        .willReturn(new PortfolioTransactionResponse(7L, null, new BigDecimal("19700.00")));

    mockMvc
        .perform(
            post("/api/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "assetId": 1,
                      "type": "SELL",
                      "tradeDate": "2026-10-07",
                      "quantity": 2,
                      "unitPrice": 80000,
                      "fee": 100,
                      "tax": 200
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionId").value(7))
        .andExpect(jsonPath("$.holding").doesNotExist())
        .andExpect(jsonPath("$.totalRealizedProfitLoss").value(19700.00));

    then(savedPortfolioService).should().transact(3L, request);
  }

  @Test
  void transactionBeforeBalanceReturnsBadRequest() throws Exception {
    given(savedPortfolioService.transact(eq(3L), any()))
        .willThrow(new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_TRADE_DATE));

    mockMvc
        .perform(
            post("/api/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"type":"BUY","tradeDate":"2026-09-30",
                     "quantity":5,"unitPrice":10000,"fee":0,"tax":0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PORTFOLIO_TRADE_DATE"));
  }

  @Test
  void updateTransactionUsesAuthenticatedMember() throws Exception {
    var request =
        new PortfolioTransactionUpdateRequest(
            LocalDate.of(2026, 10, 8),
            3L,
            new BigDecimal("81000"),
            new BigDecimal("100"),
            new BigDecimal("200"));
    given(savedPortfolioService.updateTransaction(3L, 7L, request))
        .willReturn(new PortfolioTransactionResponse(7L, null, new BigDecimal("25000.00")));

    mockMvc
        .perform(
            patch("/api/portfolio/transactions/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "tradeDate": "2026-10-08",
                      "quantity": 3,
                      "unitPrice": 81000,
                      "fee": 100,
                      "tax": 200
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(7))
        .andExpect(jsonPath("$.totalRealizedProfitLoss").value(25000.00));

    then(savedPortfolioService).should().updateTransaction(3L, 7L, request);
  }

  @Test
  void updateTransactionRejectsFutureTradeDate() throws Exception {
    mockMvc
        .perform(
            patch("/api/portfolio/transactions/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "tradeDate": "2099-01-01",
                      "quantity": 3,
                      "unitPrice": 81000,
                      "fee": 0,
                      "tax": 0
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @Test
  void deleteTransactionUsesAuthenticatedMember() throws Exception {
    given(savedPortfolioService.deleteTransaction(3L, 7L))
        .willReturn(new PortfolioTransactionResponse(7L, null, BigDecimal.ZERO));

    mockMvc
        .perform(delete("/api/portfolio/transactions/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(7))
        .andExpect(jsonPath("$.totalRealizedProfitLoss").value(0));

    then(savedPortfolioService).should().deleteTransaction(3L, 7L);
  }

  @Test
  void performanceReturnsHistoricalDataAndTransientTodayPoint() throws Exception {
    LocalDate today = LocalDate.of(2026, 10, 7);
    var currentPoint =
        new PortfolioCurrentPerformanceResponse(
            today,
            LocalDate.of(2026, 10, 6),
            new BigDecimal("65000.00"),
            new BigDecimal("70000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("7.69"),
            null,
            null);
    given(snapshotService.performance(3L, LocalDate.of(2026, 10, 1), today))
        .willReturn(
            new PortfolioPerformanceResponse(
                PortfolioPerformanceStatus.INSUFFICIENT_DATA,
                PortfolioPerformanceUnavailableReason.SNAPSHOT_DATA_INSUFFICIENT,
                null,
                null,
                List.of(),
                currentPoint));

    mockMvc
        .perform(
            get("/api/portfolio/performance")
                .queryParam("from", "2026-10-01")
                .queryParam("to", "2026-10-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentPoint.baseDate").value("2026-10-07"))
        .andExpect(jsonPath("$.currentPoint.priceBaseDate").value("2026-10-06"))
        .andExpect(jsonPath("$.currentPoint.totalEvaluationAmount").value(70000.00))
        .andExpect(jsonPath("$.currentPoint.totalReturnRate").value(7.69))
        .andExpect(jsonPath("$.currentPoint.dailyProfitLoss").value(nullValue()))
        .andExpect(jsonPath("$.currentPoint.dailyReturnRate").value(nullValue()));

    then(snapshotService)
        .should()
        .performance(3L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
  }
}
