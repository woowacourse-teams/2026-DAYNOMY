package org.grit.daynomy.search.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.service.NewsSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class NewsSearchControllerTest {

  private NewsSearchService newsSearchService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    newsSearchService = mock(NewsSearchService.class);

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    MethodValidationPostProcessor methodValidationPostProcessor =
        new MethodValidationPostProcessor();
    methodValidationPostProcessor.setValidator(validator);
    methodValidationPostProcessor.afterPropertiesSet();

    Object controller =
        methodValidationPostProcessor.postProcessAfterInitialization(
            new NewsSearchController(newsSearchService), "newsSearchController");

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void searchesNewsWithCategory() throws Exception {
    when(newsSearchService.search(eq("금리"), eq(Category.BOND), eq(1), eq(20)))
        .thenReturn(
            new NewsSearchResponse(
                List.of(
                    new NewsListItemResponse(
                        1L,
                        "기준금리 동결 가능성 확대",
                        "기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다.",
                        "https://example.com/base-rate.webp",
                        Category.BOND,
                        Instant.parse("2026-08-14T10:00:00Z"))),
                1,
                20,
                1,
                1));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "BOND"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").doesNotExist())
        .andExpect(jsonPath("$.content[0].category").value("BOND"))
        .andExpect(jsonPath("$.content[0].description").value("기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다."))
        .andExpect(jsonPath("$.content[0].imageUrl").value("https://example.com/base-rate.webp"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void returnsSuccessWhenSearchResultIsEmpty() throws Exception {
    when(newsSearchService.search(eq("금리"), eq(null), eq(1), eq(20)))
        .thenReturn(new NewsSearchResponse(List.of(), 1, 20, 0, 0));

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
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("검색어를 입력해주세요."))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());

    verifyNoInteractions(newsSearchService);
  }

  @Test
  void rejectsKeywordWithoutLetterOrDigit() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("올바른 검색어를 입력해주세요."))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());

    verifyNoInteractions(newsSearchService);
  }

  @Test
  void rejectsUnknownCategory() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "POLICY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("category"))
        .andExpect(jsonPath("$.errors[0].reason").value("지원하지 않는 값입니다."))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());

    verifyNoInteractions(newsSearchService);
  }

  @Test
  void rejectsNonNumericPage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "first"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].reason").value("타입이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());

    verifyNoInteractions(newsSearchService);
  }

  @Test
  void rejectsOutOfRangePage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].reason").value("페이지 번호는 1 이상이어야 합니다."))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());

    verifyNoInteractions(newsSearchService);
  }
}
