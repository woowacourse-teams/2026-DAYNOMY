package org.grit.daynomy.portfolio.v2.controller;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.config.ValidationConfig;
import org.grit.daynomy.portfolio.v2.service.PortfolioTransactionServiceV2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    controllers = PortfolioTransactionControllerV2.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(ValidationConfig.class)
class PortfolioTransactionControllerV2Test {

  @Autowired private MockMvc mvc;
  @MockitoBean private PortfolioTransactionServiceV2 service;

  @ParameterizedTest
  @MethodSource("invalidTransactions")
  void rejectsInvalidTransactionsBeforeCallingService(
      LocalDate tradeDate, int quantity, String tradeTime, String errorField) throws Exception {
    mvc.perform(
            post("/api/v2/users/me/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "assetId": 1,
                      "type": "BUY",
                      "tradeDate": "%s",
                      "quantity": %d,
                      "unitPrice": 100,
                      "fee": 0,
                      "tax": 0,
                      "tradeTime": "%s"
                    }
                    """
                        .formatted(tradeDate, quantity, tradeTime)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.errors[0].field").value(errorField))
        .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());

    then(service).shouldHaveNoInteractions();
  }

  private static Stream<Arguments> invalidTransactions() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    return Stream.of(
        Arguments.of(today.minusDays(1), 0, "09:35", "quantity"),
        Arguments.of(today.plusDays(1), 1, "09:35", "tradeDate"),
        Arguments.of(today.minusDays(1), 1, "25:70", "requestBody"));
  }
}
