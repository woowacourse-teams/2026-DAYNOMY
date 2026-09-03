package org.grit.daynomy.news.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsListItemResponse;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.service.AdminNewsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = AdminNewsController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminNewsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminNewsService adminNewsService;

  @Test
  @DisplayName("관리자 뉴스 등록 API는 초안 뉴스를 생성하고 201을 반환한다")
  void createNewsReturnsCreatedDraft() throws Exception {
    News news = mock(News.class);
    willReturn(1L).given(news).getId();
    willReturn("뉴스 제목").given(news).getTitle();
    willReturn("뉴스 본문").given(news).getContent();
    willReturn("뉴스 요약").given(news).getDescription();
    willReturn("https://example.com/image.png").given(news).getImageUrl();
    willReturn(null).given(news).getSource();
    willReturn("https://example.com/news/1").given(news).getSourceUrl();
    willReturn(Category.STOCK).given(news).getCategory();
    willReturn(null).given(news).getPublishedAt();
    willReturn(NewsStatus.DRAFT).given(news).getStatus();
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목", "뉴스 본문", "뉴스 요약", "https://example.com/news/1", Category.STOCK);
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
              "title": "뉴스 제목",
              "content": "뉴스 본문",
              "description": "뉴스 요약",
              "sourceUrl": "https://example.com/news/1",
              "category": "STOCK"
            }
            """
                .getBytes());
    MockMultipartFile image =
        new MockMultipartFile("image", "news.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});
    willReturn(news).given(adminNewsService).createDraft(eq(request), eq(image));

    mockMvc
        .perform(multipart("/api/admin/news").file(requestPart).file(image))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("뉴스 제목"))
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.status").value("DRAFT"));

    then(adminNewsService).should().createDraft(eq(request), eq(image));
  }

  @Test
  @DisplayName("관리자 뉴스 등록 API는 필수 입력값이 없으면 요청을 거부한다")
  void createNewsRejectsInvalidRequest() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
              "title": "",
              "content": "뉴스 본문",
              "sourceUrl": "https://example.com/news/1"
            }
            """
                .getBytes());

    mockMvc
        .perform(multipart("/api/admin/news").file(requestPart))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors").isArray());

    verifyNoInteractions(adminNewsService);
  }

  @Test
  @DisplayName("관리자 뉴스 목록 조회 API는 페이지와 상태를 서비스에 전달한다")
  void getNewsPageReturnsAdminNews() throws Exception {
    given(adminNewsService.getNewsPage(1, 15, NewsStatus.DRAFT, null))
        .willReturn(
            new AdminNewsPageResponse(
                java.util.List.of(
                    new AdminNewsListItemResponse(
                        1L,
                        "초안 뉴스",
                        "뉴스 요약",
                        null,
                        null,
                        "https://example.com/news/1",
                        Category.STOCK,
                        null,
                        NewsStatus.DRAFT,
                        null)),
                1,
                15,
                1,
                1,
                false));

    mockMvc
        .perform(
            get("/api/admin/news").param("page", "1").param("size", "15").param("status", "DRAFT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("초안 뉴스"))
        .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));

    then(adminNewsService).should().getNewsPage(1, 15, NewsStatus.DRAFT, null);
  }

  @Test
  @DisplayName("관리자 뉴스 상세 조회 API는 초안 뉴스의 상세 정보를 반환한다")
  void getNewsDetailReturnsDraftNews() throws Exception {
    News news = mock(News.class);
    willReturn(1L).given(news).getId();
    willReturn("초안 뉴스").given(news).getTitle();
    willReturn("뉴스 본문").given(news).getContent();
    willReturn("뉴스 요약").given(news).getDescription();
    willReturn(null).given(news).getImageUrl();
    willReturn(null).given(news).getSource();
    willReturn("https://example.com/news/1").given(news).getSourceUrl();
    willReturn(Category.STOCK).given(news).getCategory();
    willReturn(null).given(news).getPublishedAt();
    willReturn(NewsStatus.DRAFT).given(news).getStatus();
    willReturn(news).given(adminNewsService).getNewsDetail(1L);

    mockMvc
        .perform(get("/api/admin/news/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("초안 뉴스"))
        .andExpect(jsonPath("$.content").value("뉴스 본문"))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    then(adminNewsService).should().getNewsDetail(1L);
  }

  @Test
  @DisplayName("관리자 뉴스 수정 API는 수정 요청을 서비스에 전달하고 응답을 반환한다")
  void updateNewsReturnsUpdatedNews() throws Exception {
    News news = mock(News.class);
    willReturn(1L).given(news).getId();
    willReturn("수정 제목").given(news).getTitle();
    willReturn("수정 본문").given(news).getContent();
    willReturn("수정 요약").given(news).getDescription();
    willReturn("new-image.png").given(news).getImageUrl();
    willReturn(null).given(news).getSource();
    willReturn("https://example.com/new").given(news).getSourceUrl();
    willReturn(Category.BOND).given(news).getCategory();
    willReturn(null).given(news).getPublishedAt();
    willReturn(NewsStatus.DRAFT).given(news).getStatus();
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목", "수정 본문", "수정 요약", "https://example.com/new", Category.BOND);
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
              "title": "수정 제목",
              "content": "수정 본문",
              "description": "수정 요약",
              "sourceUrl": "https://example.com/new",
              "category": "BOND"
            }
            """
                .getBytes());
    MockMultipartFile image =
        new MockMultipartFile("image", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});
    willReturn(news).given(adminNewsService).update(eq(1L), eq(request), eq(image));

    mockMvc
        .perform(
            multipart("/api/admin/news/1")
                .file(requestPart)
                .file(image)
                .with(
                    servletRequest -> {
                      servletRequest.setMethod("PUT");
                      return servletRequest;
                    }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("수정 제목"))
        .andExpect(jsonPath("$.category").value("BOND"))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    then(adminNewsService).should().update(eq(1L), eq(request), eq(image));
  }

  @Test
  @DisplayName("관리자 뉴스 삭제 API는 204를 반환한다")
  void deleteNewsReturnsNoContent() throws Exception {
    willDoNothing().given(adminNewsService).delete(1L);

    mockMvc.perform(delete("/api/admin/news/1")).andExpect(status().isNoContent());

    then(adminNewsService).should().delete(1L);
  }
}
