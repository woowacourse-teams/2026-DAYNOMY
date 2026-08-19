package org.grit.daynomy.search.controller;

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
import org.grit.daynomy.common.GlobalExceptionHandler;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.grit.daynomy.search.service.NewsSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NewsSearchControllerTest {

  private NewsSearchRepository newsSearchRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    newsSearchRepository = mock(NewsSearchRepository.class);
    NewsSearchService service = new NewsSearchService(newsSearchRepository);
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
    when(news.getDescription()).thenReturn("기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다.");
    when(news.getImageUrl()).thenReturn("https://example.com/base-rate.webp");
    when(news.getCategory()).thenReturn(Category.BOND);
    when(news.getPublishedAt()).thenReturn(LocalDateTime.of(2026, 8, 14, 10, 0));
    when(newsSearchRepository.search(eq("금리"), eq(Category.BOND), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(news), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "BOND"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").doesNotExist())
        .andExpect(jsonPath("$.content[0].category").value("BOND"))
        .andExpect(jsonPath("$.content[0].description").value("기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다."))
        .andExpect(jsonPath("$.content[0].imageUrl").value("https://example.com/base-rate.webp"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void returnsSuccessWhenSearchResultIsEmpty() throws Exception {
    when(newsSearchRepository.search(eq("금리"), eq(null), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void requiresKeyword() throws Exception {
    mockMvc
        .perform(get("/api/search/news"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_KEYWORD_REQUIRED"))
        .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));

    verifyNoInteractions(newsSearchRepository);
  }

  @Test
  void rejectsKeywordWithoutLetterOrDigit() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_INVALID_KEYWORD"))
        .andExpect(jsonPath("$.message").value("올바른 검색어를 입력해주세요."));

    verifyNoInteractions(newsSearchRepository);
  }

  @Test
  void rejectsUnknownCategory() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "POLICY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_INVALID_CATEGORY"))
        .andExpect(jsonPath("$.message").value("존재하지 않는 카테고리입니다."));

    verifyNoInteractions(newsSearchRepository);
  }

  @Test
  void rejectsNonNumericPage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "first"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_INVALID_PAGE_CONDITION"))
        .andExpect(jsonPath("$.message").value("검색 페이지 조건이 올바르지 않습니다."));

    verifyNoInteractions(newsSearchRepository);
  }

  @Test
  void rejectsOutOfRangePage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_INVALID_PAGE_CONDITION"))
        .andExpect(jsonPath("$.message").value("검색 페이지 조건이 올바르지 않습니다."));

    verifyNoInteractions(newsSearchRepository);
  }
}
