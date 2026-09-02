package org.grit.daynomy.news.controller;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
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
    willReturn(news)
        .given(adminNewsService)
        .createDraft(
            new AdminNewsCreateRequest(
                "뉴스 제목",
                "뉴스 본문",
                "뉴스 요약",
                "https://example.com/image.png",
                "https://example.com/news/1",
                Category.STOCK));

    mockMvc
        .perform(
            post("/api/admin/news")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "뉴스 제목",
                      "content": "뉴스 본문",
                      "description": "뉴스 요약",
                      "imageUrl": "https://example.com/image.png",
                      "sourceUrl": "https://example.com/news/1",
                      "category": "STOCK"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("뉴스 제목"))
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.status").value("DRAFT"));

    then(adminNewsService)
        .should()
        .createDraft(
            new AdminNewsCreateRequest(
                "뉴스 제목",
                "뉴스 본문",
                "뉴스 요약",
                "https://example.com/image.png",
                "https://example.com/news/1",
                Category.STOCK));
  }

  @Test
  @DisplayName("관리자 뉴스 등록 API는 필수 입력값이 없으면 요청을 거부한다")
  void createNewsRejectsInvalidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/news")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "",
                      "content": "뉴스 본문",
                      "sourceUrl": "https://example.com/news/1"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors").isArray());

    verifyNoInteractions(adminNewsService);
  }
}
