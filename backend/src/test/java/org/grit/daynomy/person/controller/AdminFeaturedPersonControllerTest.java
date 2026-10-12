package org.grit.daynomy.person.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.SocialPostResponse;
import org.grit.daynomy.content.dto.SocialPostSearchResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.service.FeaturedPersonService;
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
    controllers = AdminFeaturedPersonController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminFeaturedPersonControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeaturedPersonService featuredPersonService;

  @Test
  void createPostReturnsCreatedPost() throws Exception {
    given(featuredPersonService.createPost(any(), any()))
        .willReturn(
            new FeaturedPersonPostResponse(
                3L,
                ContentSourceType.X,
                "일론 머스크 X 게시물",
                "https://x.com/elonmusk/status/12345",
                Instant.parse("2026-10-11T12:00:00Z")));

    mockMvc
        .perform(
            post("/api/admin/people/{personId}/posts", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceType": "X",
                      "title": null,
                      "url": "https://x.com/elonmusk/status/12345"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.sourceType").value("X"))
        .andExpect(jsonPath("$.title").value("일론 머스크 X 게시물"));

    then(featuredPersonService).should().createPost(any(Long.class), any(FeaturedPersonPostRequest.class));
  }

  @Test
  void searchPostsReturnsThreadsAndXCandidates() throws Exception {
    given(featuredPersonService.searchPosts(1L))
        .willReturn(
            new SocialPostSearchResponse(
                List.of(
                    new SocialPostResponse(
                        ContentSourceType.X,
                        "일론 머스크 X 게시물",
                        "https://x.com/elonmusk/status/12345",
                        ""),
                    new SocialPostResponse(
                        ContentSourceType.THREADS,
                        "일론 머스크 Threads 게시물",
                        "https://www.threads.net/@elonmusk/post/ABC",
                        "2026-10-12"))));

    mockMvc
        .perform(get("/api/admin/people/{personId}/posts/search", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].sourceType").value("X"))
        .andExpect(jsonPath("$.items[1].sourceType").value("THREADS"));

    then(featuredPersonService).should().searchPosts(1L);
  }
}
