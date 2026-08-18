package org.grit.daynomy.news.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.news.service.NewsSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NewsSearchControllerTest {

  private NewsRepository newsRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    newsRepository = mock(NewsRepository.class);
    NewsSearchService service = new NewsSearchService(newsRepository);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new NewsSearchController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void searchesNewsWithCategory() throws Exception {
    News news = mock(News.class);
    when(news.getId()).thenReturn(1L);
    when(news.getTitle()).thenReturn("기준금리 동결 가능성 확대");
    when(news.getCategory()).thenReturn(Category.BOND);
    when(news.getPublishedAt()).thenReturn(LocalDateTime.of(2026, 8, 14, 10, 0));
    when(newsRepository.search(eq("금리"), eq(Category.BOND), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(news), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "BOND"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("카테고리 필터 검색 결과를 조회했습니다."))
        .andExpect(jsonPath("$.body.category").doesNotExist())
        .andExpect(jsonPath("$.body.content[0].category").value("BOND"))
        .andExpect(jsonPath("$.body.page").value(0))
        .andExpect(jsonPath("$.body.size").value(20));
  }

  @Test
  void returnsSuccessWhenSearchResultIsEmpty() throws Exception {
    when(newsRepository.search(eq("금리"), eq(null), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("검색 결과가 없습니다."))
        .andExpect(jsonPath("$.body.content").isEmpty())
        .andExpect(jsonPath("$.body.totalElements").value(0))
        .andExpect(jsonPath("$.body.totalPages").value(0));
  }

  @Test
  void requiresKeyword() throws Exception {
    mockMvc
        .perform(get("/api/search/news"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_KEYWORD_REQUIRED"))
        .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));

    verifyNoInteractions(newsRepository);
  }

  @Test
  void rejectsKeywordWithoutLetterOrDigit() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SEARCH_KEYWORD"));

    verifyNoInteractions(newsRepository);
  }

  @Test
  void rejectsUnknownCategory() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "POLICY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

    verifyNoInteractions(newsRepository);
  }

  @Test
  void rejectsNonNumericPage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "first"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    verifyNoInteractions(newsRepository);
  }
}
