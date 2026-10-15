package org.grit.daynomy.portfolio.v2.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.config.ValidationConfig;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.v2.service.PortfolioTransactionServiceV2;
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
    controllers = PortfolioTransactionControllerV2.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ValidationConfig.class})
@EnableWebSecurity
class PortfolioTransactionControllerV2Test {
  @Autowired private MockMvc mvc;
  @MockitoBean private PortfolioTransactionServiceV2 service;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(3L, MemberRole.USER), null, java.util.List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsCreatedWithV1ResponseContract() throws Exception {
    given(service.transact(eq(3L), any()))
        .willReturn(new PortfolioTransactionResponse(7L, null, new BigDecimal("33.00")));
    mvc.perform(
            post("/api/v2/users/me/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(today(), 2)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionId").value(7))
        .andExpect(jsonPath("$.totalRealizedProfitLoss").value(33));
  }

  @Test
  void rejectsInvalidQuantityAndFutureTradeDate() throws Exception {
    mvc.perform(
            post("/api/v2/users/me/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(today(), 0)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v2/users/me/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(today().plusDays(1), 1)))
        .andExpect(status().isBadRequest());
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  private String body(LocalDate day, int quantity) {
    return """
        {"assetId":1,"type":"SELL","tradeDate":"%s","quantity":%d,
         "unitPrice":120,"fee":3,"tax":4}
        """
        .formatted(day, quantity);
  }
}
