package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.StockMasterSyncService;
import org.grit.daynomy.asset.service.StockSyncResult;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
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
    controllers = StockSyncAdminController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class StockSyncAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private StockMasterSyncService stockMasterSyncService;

  @Test
  @DisplayName("관리자 국내 주식 동기화 API는 동기화 결과를 반환한다")
  void synchronize() throws Exception {
    given(stockMasterSyncService.synchronize())
        .willReturn(new StockSyncResult(LocalDate.of(2026, 9, 18), 2500, 20, 2470, 10));

    mockMvc
        .perform(post("/api/admin/stocks/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-09-18"))
        .andExpect(jsonPath("$.syncedCount").value(2500))
        .andExpect(jsonPath("$.createdCount").value(20))
        .andExpect(jsonPath("$.updatedCount").value(2470))
        .andExpect(jsonPath("$.delistedCount").value(10));

    then(stockMasterSyncService).should().synchronize();
  }
}
