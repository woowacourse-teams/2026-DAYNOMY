package org.grit.daynomy.portfolio.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.portfolio.dto.PortfolioCurrentPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingCreateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.SavedPortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @Test
  void addHoldingUsesAuthenticatedMember() throws Exception {
    var request = new SavedPortfolioHoldingCreateRequest(1L, 10L, new BigDecimal("65000"));
    given(savedPortfolioService.add(3L, request))
        .willReturn(
            new SavedPortfolioHoldingResponse(
                1L,
                "005930",
                "삼성전자",
                AssetCategory.STOCK,
                StockMarket.KOSPI,
                10L,
                new BigDecimal("65000")));

    mockMvc
        .perform(
            post("/api/portfolio/holdings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assetId":1,"quantity":10,"averagePurchasePrice":65000}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assetId").value(1))
        .andExpect(jsonPath("$.quantity").value(10));

    then(savedPortfolioService).should().add(3L, request);
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
            new BigDecimal("1000.00"),
            new BigDecimal("1.45"));
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
        .andExpect(jsonPath("$.currentPoint.totalReturnRate").value(7.69));

    then(snapshotService)
        .should()
        .performance(3L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
  }
}
