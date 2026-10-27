package org.grit.daynomy.emailsubscription.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.service.CommonStockAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = EmailAnalysisAdminController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmailAnalysisAdminControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private CommonStockAnalysisService service;
  private final String path = "/api/admin/email-analyses";

  @Test
  void mapsOptionalFiltersAndUsesBoundedPagination() throws Exception {
    mvc.perform(
            get(path)
                .param("status", "VERIFIED")
                .param("assetId", "7")
                .param("page", "2")
                .param("size", "10"))
        .andExpect(status().isOk());
    verify(service).listForAdmin(CommonStockAnalysis.Status.VERIFIED, 7L, PageRequest.of(2, 10));
    mvc.perform(get(path)).andExpect(status().isOk());
    verify(service).listForAdmin(null, null, PageRequest.of(0, 20));
    mvc.perform(get(path + "/9")).andExpect(status().isOk());
    verify(service).detailForAdmin(9L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?page=-1",
        "?size=0",
        "?size=101",
        "?assetId=0",
        "?assetId=-1",
        "?status=UNKNOWN"
      })
  void invalidListParametersCannotReachTheService(String query) throws Exception {
    mvc.perform(get(path + query)).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void invalidDetailIdentifierIsRejected() throws Exception {
    mvc.perform(get(path + "/0")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
