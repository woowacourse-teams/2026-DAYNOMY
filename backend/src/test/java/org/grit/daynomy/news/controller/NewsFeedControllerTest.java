package org.grit.daynomy.news.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.news.domain.NewsFeedSort;
import org.grit.daynomy.news.domain.NewsFeedSource;
import org.grit.daynomy.news.dto.NewsFeedResponse;
import org.grit.daynomy.news.service.NewsFeedService;
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
    controllers = NewsFeedController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class NewsFeedControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private NewsFeedService service;

  @Test
  void usesNewsHomeDefaults() throws Exception {
    given(service.getFeed(NewsFeedSource.ALL, 1, 8, NewsFeedSort.LATEST))
        .willReturn(new NewsFeedResponse(List.of(), 1, 8, 0, 0));

    mockMvc
        .perform(get("/api/news/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(8));

    then(service).should().getFeed(NewsFeedSource.ALL, 1, 8, NewsFeedSort.LATEST);
  }

  @Test
  void appliesSourcePageAndPopularitySort() throws Exception {
    given(service.getFeed(NewsFeedSource.DAYNOMY, 2, 4, NewsFeedSort.POPULAR))
        .willReturn(new NewsFeedResponse(List.of(), 2, 4, 0, 0));

    mockMvc
        .perform(
            get("/api/news/feed")
                .param("source", "DAYNOMY")
                .param("page", "2")
                .param("size", "4")
                .param("sort", "POPULAR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2));

    then(service).should().getFeed(NewsFeedSource.DAYNOMY, 2, 4, NewsFeedSort.POPULAR);
  }

  @Test
  void rejectsInvalidFeedConditions() throws Exception {
    mockMvc.perform(get("/api/news/feed").param("page", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/news/feed").param("size", "101")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/news/feed").param("source", "UNKNOWN"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/news/feed").param("sort", "UNKNOWN"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }
}
