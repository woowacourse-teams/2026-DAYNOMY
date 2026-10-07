package org.grit.daynomy.investmentcapacity.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.investmentcapacity.domain.PlanStatus;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanRequest;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanResponse;
import org.grit.daynomy.investmentcapacity.service.InvestmentPlanService;
import org.grit.daynomy.member.domain.MemberRole;
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
    controllers = InvestmentPlanController.class,
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@EnableWebSecurity
class InvestmentPlanControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private InvestmentPlanService investmentPlanService;

  @BeforeEach
  void setUpAuthentication() {
    AuthenticatedMember member = new AuthenticatedMember(3L, MemberRole.USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsNoContentWhenPlanHasNotBeenSaved() throws Exception {
    given(investmentPlanService.get(3L)).willReturn(null);

    mockMvc.perform(get("/api/users/me/investment-plan")).andExpect(status().isNoContent());
  }

  @Test
  void savesPlanForAuthenticatedMember() throws Exception {
    InvestmentPlanRequest request =
        new InvestmentPlanRequest(
            LocalDate.of(2000, 1, 1),
            3_000_000,
            1_000_000,
            0,
            0,
            0,
            0,
            3_000_000,
            0,
            0,
            0,
            10_000_000,
            24,
            "STABLE",
            "regular-savings");
    InvestmentPlanResponse response =
        new InvestmentPlanResponse(
            request.birthDate(),
            request.monthlyIncome(),
            request.monthlyFixedExpense(),
            request.monthlyVariableExpense(),
            request.irregularExpenseReserve(),
            request.emergencyFundContribution(),
            request.monthlyDebtRepayment(),
            request.currentCash(),
            request.existingDepositSavings(),
            request.investmentAssets(),
            request.otherAssets(),
            request.currentCash()
                + request.existingDepositSavings()
                + request.investmentAssets()
                + request.otherAssets(),
            0,
            request.goalAmount(),
            request.goalMonths(),
            6_000_000,
            3_000_000,
            2_000_000,
            416_667,
            PlanStatus.ADJUSTABLE,
            "비상금을 함께 준비해요.",
            "STABLE",
            "regular-savings",
            List.of(),
            List.of());
    given(investmentPlanService.save(3L, request)).willReturn(response);

    mockMvc
        .perform(
            put("/api/users/me/investment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "birthDate":"2000-01-01",
                      "monthlyIncome":3000000,
                      "monthlyFixedExpense":1000000,
                      "monthlyVariableExpense":0,
                      "irregularExpenseReserve":0,
                      "emergencyFundContribution":0,
                      "monthlyDebtRepayment":0,
                      "currentCash":3000000,
                      "existingDepositSavings":0,
                      "investmentAssets":0,
                      "otherAssets":0,
                      "goalAmount":10000000,
                      "goalMonths":24,
                      "selectedScenario":"STABLE",
                      "selectedProductId":"regular-savings"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goalAmount").value(10_000_000))
        .andExpect(jsonPath("$.requiredMonthlySaving").value(416_667));

    then(investmentPlanService).should().save(3L, request);
  }
}
