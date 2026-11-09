package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.grit.daynomy.asset.service.StockSyncResult;
import org.grit.daynomy.asset.service.UsStockSyncResult;
import org.grit.daynomy.asset.service.UsStockSyncService;
import org.grit.daynomy.asset.service.UsStockSyncWorkflowService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
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
    controllers = UsStockSyncAdminController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class UsStockSyncAdminControllerTest {
  private static final String PRICE_SYNC_PATH = "/api/admin/stocks/us/prices/sync";
  private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 23);

  @Autowired private MockMvc mvc;
  @MockitoBean private UsStockSyncService service;
  @MockitoBean private UsStockSyncWorkflowService workflow;

  @Test
  void synchronizesPricesForRequestedDate() throws Exception {
    given(service.synchronizePrices(BASE_DATE))
        .willReturn(new StockPriceSyncResult(BASE_DATE, 12, 7, 4, 1));

    mvc.perform(post(PRICE_SYNC_PATH).param("date", "2026-10-23"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-10-23"))
        .andExpect(jsonPath("$.receivedCount").value(12))
        .andExpect(jsonPath("$.createdCount").value(7))
        .andExpect(jsonPath("$.updatedCount").value(4))
        .andExpect(jsonPath("$.skippedCount").value(1));
  }

  @Test
  void allowsOmittingPriceSyncDate() throws Exception {
    given(service.synchronizePrices(null))
        .willReturn(new StockPriceSyncResult(BASE_DATE, 8, 5, 3, 0));

    mvc.perform(post(PRICE_SYNC_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-10-23"));
  }

  @Test
  void rejectsMalformedPriceSyncDate() throws Exception {
    mvc.perform(post(PRICE_SYNC_PATH).param("date", "2026/10/23"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("date"));

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void returnsMasterSyncResultWhenOnlyKoreanNameSyncFails() throws Exception {
    given(workflow.synchronize())
        .willReturn(
            new UsStockSyncResult(
                new StockSyncResult(BASE_DATE, 10, 4, 6, 1),
                null,
                AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_FAILED.code()));

    mvc.perform(post("/api/admin/stocks/us/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-10-23"))
        .andExpect(jsonPath("$.syncedCount").value(10))
        .andExpect(jsonPath("$.createdCount").value(4))
        .andExpect(jsonPath("$.updatedCount").value(6))
        .andExpect(jsonPath("$.delistedCount").value(1))
        .andExpect(jsonPath("$.koreanNames").doesNotExist())
        .andExpect(jsonPath("$.koreanNameErrorCode").value("US_STOCK_KOREAN_NAME_SYNC_FAILED"));
  }
}
