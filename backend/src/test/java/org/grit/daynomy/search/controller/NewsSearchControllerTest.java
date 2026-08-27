package org.grit.daynomy.search.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.service.NewsSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
    controllers = NewsSearchController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class NewsSearchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NewsSearchService newsSearchService;

  @Test
  @DisplayName("뉴스 검색 API는 검색 조건을 서비스에 전달하고 페이지 응답을 반환한다")
  void searchNewsWithCategory() throws Exception {
    given(newsSearchService.search(eq("금리"), eq(Category.BOND), eq(1), eq(20)))
        .willReturn(
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
        .andExpect(jsonPath("$.content[0].category").value("BOND"))
        .andExpect(jsonPath("$.content[0].description").value("기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다."))
        .andExpect(jsonPath("$.content[0].imageUrl").value("https://example.com/base-rate.webp"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20));

    then(newsSearchService).should().search("금리", Category.BOND, 1, 20);
  }

  @Test
  @DisplayName("뉴스 검색 API는 검색 결과가 없으면 빈 페이지를 반환한다")
  void searchNewsReturnsEmptyPage() throws Exception {
    given(newsSearchService.search(eq("금리"), eq(null), eq(1), eq(20)))
        .willReturn(new NewsSearchResponse(List.of(), 1, 20, 0, 0));

    mockMvc
        .perform(get("/api/search/news").param("q", "금리"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));

    then(newsSearchService).should().search("금리", null, 1, 20);
  }

  @Test
  @DisplayName("뉴스 검색 API는 검색어가 없으면 요청을 거부한다")
  void searchNewsRejectsMissingKeyword() throws Exception {
    mockMvc
        .perform(get("/api/search/news"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("검색어를 입력해주세요."));

    verifyNoInteractions(newsSearchService);
  }

  @Test
  @DisplayName("뉴스 검색 API는 문자나 숫자가 없는 검색어를 거부한다")
  void searchNewsRejectsInvalidKeyword() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("올바른 검색어를 입력해주세요."));

    verifyNoInteractions(newsSearchService);
  }

  @Test
  @DisplayName("뉴스 검색 API는 지원하지 않는 카테고리를 거부한다")
  void searchNewsRejectsUnknownCategory() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("category", "POLICY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("category"))
        .andExpect(jsonPath("$.errors[0].reason").value("지원하지 않는 값입니다."));

    verifyNoInteractions(newsSearchService);
  }

  @Test
  @DisplayName("뉴스 검색 API는 숫자가 아닌 페이지 번호를 거부한다")
  void searchNewsRejectsNonNumericPage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "first"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].reason").value("타입이 올바르지 않습니다."));

    verifyNoInteractions(newsSearchService);
  }

  @Test
  @DisplayName("뉴스 검색 API는 1보다 작은 페이지 번호를 거부한다")
  void searchNewsRejectsOutOfRangePage() throws Exception {
    mockMvc
        .perform(get("/api/search/news").param("q", "금리").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].reason").value("페이지 번호는 1 이상이어야 합니다."));

    verifyNoInteractions(newsSearchService);
  }
}
