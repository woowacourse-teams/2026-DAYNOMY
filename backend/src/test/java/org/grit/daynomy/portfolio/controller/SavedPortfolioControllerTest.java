package org.grit.daynomy.portfolio.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.config.ValidationConfig;
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
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.SavedPortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
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
@Import({GlobalExceptionHandler.class, ValidationConfig.class})
@EnableWebSecurity
class SavedPortfolioControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SavedPortfolioService savedPortfolioService;
  @MockitoBean private PortfolioSnapshotService snapshotService;
  @MockitoBean private PortfolioPriceHistoryService priceHistoryService;
  private MockedStatic<Clock> clocks;

  @BeforeEach
  void setUpAuthentication() {
    Instant now = Instant.parse("2026-10-13T15:00:00Z");
    Clock utcClock = Clock.fixed(now, ZoneOffset.UTC);
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    Clock seoulClock = Clock.fixed(now, seoul);
    clocks = mockStatic(Clock.class, CALLS_REAL_METHODS);
    clocks.when(Clock::systemDefaultZone).thenReturn(utcClock);
    clocks.when(() -> Clock.system(seoul)).thenReturn(seoulClock);

    var member = new AuthenticatedMember(3L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }

  @Test
  void preparePerformanceUsesAuthenticatedMemberAndReturnsCollectionStatus() throws Exception {
    given(
            priceHistoryService.prepare(
                3L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), true))
        .willReturn(PortfolioPriceHistoryService.Status.PROCESSING);

    mockMvc
        .perform(
            post(
                "/api/users/me/portfolio/performance/prepare?from=2026-10-01&to=2026-10-14&retry=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROCESSING"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/portfolio/transactions/import", "/api/portfolio/transactions/bulk"})
  void bulkTransactionsValidateEveryRowBeforeCallingService(String path) throws Exception {
    mockMvc
        .perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
      {"transactions":[{"assetId":1,"type":"BUY","tradeDate":"2026-10-01","quantity":1,"unitPrice":100,"fee":0,"tax":0},
      {"assetId":2,"type":"BUY","tradeDate":"2026-10-01","quantity":0,"unitPrice":100,"fee":0,"tax":0}]}
      """))
        .andExpect(status().isBadRequest());
    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @Test
  void bulkHoldingValidationRejectsEmptyAndInvalidRows() throws Exception {
    for (String body :
        List.of(
            "{\"holdings\":[]}",
            "{\"holdings\":[{\"assetId\":1,\"quantity\":1,\"averagePurchasePrice\":0}]}")) {
      mockMvc
          .perform(
              patch("/api/portfolio/holdings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @Test
  void openingBalanceValidationRejectsMissingAndInvalidValues() throws Exception {
    for (String body :
        List.of(
            "{\"holdings\":[]}",
            "{\"holdings\":[{\"assetId\":1,\"quantity\":0,\"averagePurchasePrice\":100}]}"))
      mockMvc
          .perform(
              post("/api/portfolio/holdings/bulk")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
    clocks.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/portfolio/holdings", "/api/users/me/portfolio/holdings"})
  void retiredHoldingCreationMethodIsNotAllowed(String path) throws Exception {
    mockMvc
        .perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"quantity":10,"averagePurchasePrice":65000}
                    """))
        .andExpect(status().isMethodNotAllowed());

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

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-13", "2026-10-14"})
  void transactionAcceptsPastAndTodayInSeoulWhenUtcIsYesterday(String tradeDate) throws Exception {
    var request =
        new PortfolioTransactionRequest(
            1L,
            PortfolioTransactionType.BUY,
            LocalDate.parse(tradeDate),
            3L,
            new BigDecimal("81000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    given(savedPortfolioService.transact(3L, request))
        .willReturn(new PortfolioTransactionResponse(7L, null, BigDecimal.ZERO));

    mockMvc
        .perform(
            post("/api/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"type":"BUY","tradeDate":"%s",
                     "quantity":3,"unitPrice":81000,"fee":0,"tax":0}
                    """
                        .formatted(tradeDate)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionId").value(7));

    then(savedPortfolioService).should().transact(3L, request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-13", "2026-10-14"})
  void updateTransactionAcceptsPastAndTodayInSeoulWhenUtcIsYesterday(String tradeDate)
      throws Exception {
    var request =
        new PortfolioTransactionUpdateRequest(
            LocalDate.parse(tradeDate),
            3L,
            new BigDecimal("81000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    given(savedPortfolioService.updateTransaction(3L, 7L, request))
        .willReturn(new PortfolioTransactionResponse(7L, null, BigDecimal.ZERO));

    mockMvc
        .perform(
            patch("/api/portfolio/transactions/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tradeDate":"%s","quantity":3,"unitPrice":81000,"fee":0,"tax":0}
                    """
                        .formatted(tradeDate)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(7));

    then(savedPortfolioService).should().updateTransaction(3L, 7L, request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-15", "2099-01-01"})
  void transactionRejectsFutureTradeDateInSeoul(String tradeDate) throws Exception {
    mockMvc
        .perform(
            post("/api/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"type":"BUY","tradeDate":"%s",
                     "quantity":3,"unitPrice":81000,"fee":0,"tax":0}
                    """
                        .formatted(tradeDate)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("tradeDate"));

    then(savedPortfolioService).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-15", "2099-01-01"})
  void updateTransactionRejectsFutureTradeDate(String tradeDate) throws Exception {
    mockMvc
        .perform(
            patch("/api/portfolio/transactions/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "tradeDate": "%s",
                      "quantity": 3,
                      "unitPrice": 81000,
                      "fee": 0,
                      "tax": 0
                    }
                    """
                        .formatted(tradeDate)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("tradeDate"));

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
