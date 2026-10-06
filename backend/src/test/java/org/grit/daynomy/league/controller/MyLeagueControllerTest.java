package org.grit.daynomy.league.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.league.service.InvestorProfileService;
import org.grit.daynomy.league.service.LeagueService;
import org.grit.daynomy.league.service.PortfolioTransactionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    controllers = MyLeagueController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MyLeagueControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private InvestorProfileService profiles;
  @MockitoBean private PortfolioTransactionService transactions;
  @MockitoBean private LeagueService league;

  @ParameterizedTest
  @ValueSource(strings = {"-1", "101", "1.234"})
  void rejectsInvalidLossRateBeforeWriting(String rate) throws Exception {
    mvc.perform(
            post("/api/users/me/portfolio/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"requestKey":"decision-1","assetId":1,"decision":{"reason":"보유 이유",
        "expectedHoldingPeriod":"OVER_SIX_MONTHS","expectedChange":"성장","invalidationCondition":"악화",
        "maximumAcceptableLossRate":%s}}
        """
                        .formatted(rate)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"requestKey\":\"decision-1\",\"assetId\":1,\"decision\":null}",
        "{\"requestKey\":\"\",\"assetId\":0,\"decision\":{}}"
      })
  void rejectsMissingOrMalformedDecisionBeforeWriting(String body) throws Exception {
    mvc.perform(
            post("/api/users/me/portfolio/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(transactions);
  }
}
