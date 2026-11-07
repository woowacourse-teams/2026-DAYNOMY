package org.grit.daynomy.keyword.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.keyword.dto.KeywordsResponse;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(
    controllers = KeywordController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class KeywordControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean KeywordService service;

  @Test
  void convertsNewsIdAndReturnsEmptyKeywords() throws Exception {
    when(service.getKeywords(42L)).thenReturn(new KeywordsResponse(List.of()));
    mvc.perform(get("/api/news/42/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords").isEmpty());
    verify(service).getKeywords(42L);
  }

  @Test
  void translatesMissingNewsToThePublicErrorContract() throws Exception {
    when(service.getKeywords(42L)).thenThrow(new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    mvc.perform(get("/api/news/42/keywords"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));
  }

  @Test
  void rejectsNonNumericNewsIdBeforeCallingService() throws Exception {
    mvc.perform(get("/api/news/invalid/keywords"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("newsId"));
    verifyNoInteractions(service);
  }
}
