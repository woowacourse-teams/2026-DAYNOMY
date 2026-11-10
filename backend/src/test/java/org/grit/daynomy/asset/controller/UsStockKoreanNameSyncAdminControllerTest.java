package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.service.UsStockKoreanNameSyncResult;
import org.grit.daynomy.asset.service.UsStockKoreanNameSyncService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
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
    controllers = UsStockKoreanNameSyncAdminController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class UsStockKoreanNameSyncAdminControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean UsStockKoreanNameSyncService service;

  @Test
  void returnsDownloadAndMatchingCounts() throws Exception {
    given(service.synchronize()).willReturn(new UsStockKoreanNameSyncResult(100, 70, 20, 10, 20));

    mvc.perform(post("/api/admin/stocks/us/korean-names/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.downloadedCount").value(100))
        .andExpect(jsonPath("$.matchedCount").value(70))
        .andExpect(jsonPath("$.updatedCount").value(20))
        .andExpect(jsonPath("$.unmatchedCount").value(10))
        .andExpect(jsonPath("$.skippedCount").value(20));
  }

  @Test
  void reportsAlreadyRunningConflict() throws Exception {
    given(service.synchronize())
        .willThrow(new BusinessException(AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_ALREADY_RUNNING));

    mvc.perform(post("/api/admin/stocks/us/korean-names/sync"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("US_STOCK_KOREAN_NAME_SYNC_ALREADY_RUNNING"));
  }
}
