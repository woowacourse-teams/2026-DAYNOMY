package org.grit.daynomy.search.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.service.ContentSearchService;
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
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
    controllers = ContentSearchController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class ContentSearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ContentSearchService service;

  @Test
  void appliesSourceAndPageParameters() throws Exception {
    given(service.search("금리", ContentSearchSource.YOUTUBE, 2, 10, ContentSearchSort.LATEST))
        .willReturn(new ContentSearchResponse(List.of(), 2, 10, 0, 0));

    mockMvc
        .perform(
            get("/api/search/contents")
                .param("q", "금리")
                .param("source", "YOUTUBE")
                .param("page", "2")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(2));

    then(service)
        .should()
        .search("금리", ContentSearchSource.YOUTUBE, 2, 10, ContentSearchSort.LATEST);
  }

  @Test
  void acceptsPopularitySort() throws Exception {
    given(service.search("금리", ContentSearchSource.ALL, 1, 12, ContentSearchSort.POPULAR))
        .willReturn(new ContentSearchResponse(List.of(), 1, 12, 0, 0));

    mockMvc
        .perform(get("/api/search/contents").param("q", "금리").param("sort", "POPULAR"))
        .andExpect(status().isOk());

    then(service).should().search("금리", ContentSearchSource.ALL, 1, 12, ContentSearchSort.POPULAR);
  }

  @Test
  void rejectsInvalidSearchConditions() throws Exception {
    mockMvc
        .perform(get("/api/search/contents").param("q", "  "))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/search/contents").param("q", "금리").param("source", "STOCK"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/search/contents").param("q", "금리").param("page", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/search/contents").param("q", "금리").param("sort", "UNKNOWN"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
