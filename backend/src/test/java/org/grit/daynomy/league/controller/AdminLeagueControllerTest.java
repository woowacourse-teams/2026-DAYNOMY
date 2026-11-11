package org.grit.daynomy.league.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = AdminLeagueController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminLeagueControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private LeagueReturnService service;

  @Test
  void returnsCalculatedCount() throws Exception {
    given(service.recalculate()).willReturn(3);
    mvc.perform(post("/api/admin/league/recalculate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculatedCount").value(3));
    then(service).should().recalculate();
  }

  @Test
  void calculationFailureReturnsCommonErrorWithoutLeakingException() throws Exception {
    given(service.recalculate())
        .willThrow(new IllegalStateException("internal calculation details"));
    mvc.perform(post("/api/admin/league/recalculate"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.grit.daynomy.common.exception.CommonErrorCode.INTERNAL_SERVER_ERROR
                        .message()));
  }
}
