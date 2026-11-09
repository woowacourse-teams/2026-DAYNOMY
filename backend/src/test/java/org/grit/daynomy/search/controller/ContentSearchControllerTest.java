package org.grit.daynomy.search.controller;

import static org.hamcrest.Matchers.hasItem;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

  /** 검색어 파라미터 자체가 없는 요청과 값이 유효하지 않은 요청을 모두 HTTP 경계에서 차단한다. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "!%_"})
  void rejectsMissingOrInvalidKeyword(String keyword) throws Exception {
    var request = get("/api/search/contents");
    if (keyword != null) request.param("q", keyword);
    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[*].field", hasItem("keyword")))
        .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsKeywordLongerThanOneHundredCharacters() throws Exception {
    mockMvc
        .perform(get("/api/search/contents").param("q", "가".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("검색어는 100자 이하여야 합니다."));
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @CsvSource({
    "source,STOCK",
    "sort,UNKNOWN",
    "page,0",
    "page,-1",
    "page,first",
    "size,0",
    "size,101",
    "size,large"
  })
  void rejectsInvalidSearchConditions(String field, String value) throws Exception {
    mockMvc
        .perform(get("/api/search/contents").param("q", "금리").param(field, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
        .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100})
  void acceptsKeywordAndSizeBoundaries(int size) throws Exception {
    String keyword = "가".repeat(100);
    given(service.search(keyword, ContentSearchSource.ALL, 1, size, ContentSearchSort.LATEST))
        .willReturn(new ContentSearchResponse(List.of(), 1, size, 0, 0));
    mockMvc
        .perform(
            get("/api/search/contents").param("q", keyword).param("size", Integer.toString(size)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(size))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
    then(service)
        .should()
        .search(keyword, ContentSearchSource.ALL, 1, size, ContentSearchSort.LATEST);
  }
}
