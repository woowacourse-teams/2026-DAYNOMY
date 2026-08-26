package org.grit.daynomy.news.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.news.dto.NewsPageResponse;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.service.NewsService;
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
    controllers = NewsController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class NewsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NewsService newsService;

  @Test
  @DisplayName("뉴스 목록 조회 API는 페이지 요청 값을 서비스에 전달하고 응답을 반환한다")
  void findNewsReturnsPagedNews() throws Exception {
    given(newsService.getNewsPage(eq(1), eq(15), eq(null)))
        .willReturn(
            new NewsPageResponse(
                List.of(
                    new NewsListItemResponse(
                        1L,
                        "stock news",
                        "description",
                        "image.png",
                        Category.STOCK,
                        Instant.parse("2026-08-17T10:00:00Z"))),
                1,
                15,
                1,
                1,
                false));

    mockMvc
        .perform(get("/api/news").param("page", "1").param("size", "15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].title").value("stock news"))
        .andExpect(jsonPath("$.items[0].category").value("STOCK"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(15))
        .andExpect(jsonPath("$.totalElements").value(1));

    then(newsService).should().getNewsPage(1, 15, null);
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 카테고리 요청 값을 서비스에 전달한다")
  void findNewsFiltersByCategory() throws Exception {
    given(newsService.getNewsPage(eq(2), eq(10), eq(Category.REAL_ESTATE)))
        .willReturn(new NewsPageResponse(List.of(), 2, 10, 0, 0, false));

    mockMvc
        .perform(
            get("/api/news")
                .param("page", "2")
                .param("size", "10")
                .param("category", "REAL_ESTATE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(10));

    then(newsService).should().getNewsPage(2, 10, Category.REAL_ESTATE);
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 잘못된 페이지 번호에 에러 응답을 반환한다")
  void findNewsRejectsInvalidPage() throws Exception {
    mockMvc
        .perform(get("/api/news").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].reason").value("페이지 번호는 1 이상이어야 합니다."));

    verifyNoInteractions(newsService);
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 잘못된 페이지 크기에 에러 응답을 반환한다")
  void findNewsRejectsInvalidSize() throws Exception {
    mockMvc
        .perform(get("/api/news").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("size"))
        .andExpect(jsonPath("$.errors[0].reason").value("페이지 크기는 100 이하여야 합니다."));

    verifyNoInteractions(newsService);
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 지원하지 않는 카테고리에 에러 응답을 반환한다")
  void findNewsRejectsUnknownCategory() throws Exception {
    mockMvc
        .perform(get("/api/news").param("category", "POLICY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors[0].field").value("category"))
        .andExpect(jsonPath("$.errors[0].reason").value("지원하지 않는 값입니다."));

    verifyNoInteractions(newsService);
  }

  @Test
  @DisplayName("오늘의 뉴스 조회 API는 오늘 뉴스를 반환한다")
  void findTodayNewsReturnsNews() throws Exception {
    given(newsService.getTodayNews())
        .willReturn(
            new NewsListItemResponse(
                1L,
                "today news",
                "description",
                "image.png",
                Category.REAL_ESTATE,
                Instant.parse("2026-08-21T09:00:00Z")));

    mockMvc
        .perform(get("/api/news/today"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("today news"))
        .andExpect(jsonPath("$.category").value("REAL_ESTATE"));

    then(newsService).should().getTodayNews();
  }

  @Test
  @DisplayName("오늘의 뉴스 조회 API는 오늘 뉴스가 없으면 빈 응답을 반환한다")
  void findTodayNewsReturnsEmptyBodyWhenMissing() throws Exception {
    given(newsService.getTodayNews()).willReturn(null);

    mockMvc
        .perform(get("/api/news/today"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    then(newsService).should().getTodayNews();
  }

  @Test
  @DisplayName("뉴스 상세 조회 API는 경로 변수를 서비스에 전달하고 응답을 반환한다")
  void findNewsDetailReturnsNews() throws Exception {
    given(newsService.getNewsDetail(1L))
        .willReturn(
            new NewsDetailResponse(
                1L,
                "detail news",
                "content",
                "description",
                "image.png",
                NewsSource.DART,
                "https://example.com/1",
                Category.STOCK,
                Instant.parse("2026-08-17T10:00:00Z")));

    mockMvc
        .perform(get("/api/news/{id}", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("detail news"))
        .andExpect(jsonPath("$.content").value("content"))
        .andExpect(jsonPath("$.source").value("DART"));

    then(newsService).should().getNewsDetail(1L);
  }

  @Test
  @DisplayName("뉴스 상세 조회 API는 서비스 예외를 에러 응답으로 변환한다")
  void findNewsDetailReturnsNotFound() throws Exception {
    given(newsService.getNewsDetail(999L))
        .willThrow(new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    mockMvc
        .perform(get("/api/news/{id}", 999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 뉴스를 찾을 수 없습니다."));
  }
}
