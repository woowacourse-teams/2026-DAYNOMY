package org.grit.daynomy.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionBatchResultResponse;
import org.grit.daynomy.content.dto.ContentCollectionManualRequest;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.dto.ContentCollectionSourceRequest;
import org.grit.daynomy.content.dto.ContentCollectionSourceResponse;
import org.grit.daynomy.content.service.ContentCollectionBatchService;
import org.grit.daynomy.content.service.ContentCollectionManualService;
import org.grit.daynomy.content.service.ContentCollectionService;
import org.grit.daynomy.content.service.ContentCollectionSourceService;
import org.junit.jupiter.api.Test;
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
    controllers = AdminContentCollectionController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminContentCollectionControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ContentCollectionSourceService sourceService;
  @MockitoBean private ContentCollectionService collectionService;
  @MockitoBean private ContentCollectionManualService manualService;
  @MockitoBean private ContentCollectionBatchService batchService;

  @Test
  void createSourceReturnsCreatedSource() throws Exception {
    given(sourceService.create(any()))
        .willReturn(
            new ContentCollectionSourceResponse(
                1L,
                "경제 채널",
                ContentSourceType.YOUTUBE,
                "https://www.youtube.com/@economy",
                null,
                null,
                true,
                null));

    mockMvc
        .perform(
            post("/api/admin/content-collection-sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "경제 채널",
                      "sourceType": "YOUTUBE",
                      "sourceUrl": "https://www.youtube.com/@economy",
                      "assetId": null,
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.sourceType").value("YOUTUBE"));

    then(sourceService).should().create(any(ContentCollectionSourceRequest.class));
  }

  @Test
  void createSourceRejectsInvalidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "sourceType": null,
                      "sourceUrl": "not-a-url",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isBadRequest());

    then(sourceService).shouldHaveNoInteractions();
  }

  @Test
  void collectAllReturnsBatchResult() throws Exception {
    given(batchService.collectAllEnabled())
        .willReturn(new ContentCollectionBatchResultResponse(3, 2, 1, 8, 5, 3, 4));

    mockMvc
        .perform(post("/api/admin/content-collection-sources/collect-all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceCount").value(3))
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(1))
        .andExpect(jsonPath("$.linkedCount").value(4));

    then(batchService).should().collectAllEnabled();
  }

  @Test
  void registerManualContentReturnsCreatedResult() throws Exception {
    given(manualService.register(org.mockito.ArgumentMatchers.eq(1L), any()))
        .willReturn(new ContentCollectionResultResponse(1L, 1, 1, 0, 2));

    mockMvc
        .perform(
            post("/api/admin/content-collection-sources/1/contents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "url": "https://x.com/hankyungmedia/status/2115009742426808418",
                      "title": "반도체 전망",
                      "description": "삼성전자와 SK하이닉스 전망",
                      "publishedDate": "2026-10-27"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.savedCount").value(1))
        .andExpect(jsonPath("$.linkedCount").value(2));

    then(manualService)
        .should()
        .register(org.mockito.ArgumentMatchers.eq(1L), any(ContentCollectionManualRequest.class));
  }
}
