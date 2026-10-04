package org.grit.daynomy.content.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.dto.YouTubeSearchResponse;
import org.grit.daynomy.content.dto.YouTubeVideoResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = AdminAssetContentController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminAssetContentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AssetContentService assetContentService;

  @Test
  void searchYouTubeReturnsVideoCandidates() throws Exception {
    given(assetContentService.searchYouTube(1L, "삼성전자 005930"))
        .willReturn(
            new YouTubeSearchResponse(
                List.of(
                    new YouTubeVideoResponse(
                        "삼성전자 분석",
                        "https://www.youtube.com/watch?v=abc123",
                        "DAYNOMY",
                        "2026-10-04T00:00:00Z",
                        "https://i.ytimg.com/high.jpg"))));

    mockMvc
        .perform(
            get("/api/admin/assets/{assetId}/contents/youtube-search", 1L)
                .queryParam("keyword", "삼성전자 005930"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("삼성전자 분석"))
        .andExpect(jsonPath("$.items[0].url").value("https://www.youtube.com/watch?v=abc123"))
        .andExpect(jsonPath("$.items[0].channelTitle").value("DAYNOMY"));

    then(assetContentService).should().searchYouTube(1L, "삼성전자 005930");
  }
}
