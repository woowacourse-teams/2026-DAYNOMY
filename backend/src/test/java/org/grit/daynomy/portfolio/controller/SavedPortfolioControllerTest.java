package org.grit.daynomy.portfolio.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.member.domain.MemberRole;
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
            post("/api/users/me/portfolio/holdings")
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
}
