package org.grit.daynomy.content.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.AssetContentResponse;
import org.grit.daynomy.content.dto.AssetContentsResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.junit.jupiter.api.DisplayName;
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
    controllers = AssetContentController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AssetContentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AssetContentService assetContentService;

  @Test
  @DisplayName("종목 관련 자료 조회 API는 제목과 URL을 반환한다")
  void getContents() throws Exception {
    given(assetContentService.getContents(1L))
        .willReturn(
            new AssetContentsResponse(
                1L,
                "005930",
                "삼성전자",
                List.of(
                    new AssetContentResponse(
                        10L,
                        1L,
                        ContentSourceType.YOUTUBE,
                        "삼성전자 분석",
                        "https://youtu.be/example",
                        "https://i.ytimg.com/example.jpg",
                        null))));

    mockMvc
        .perform(get("/api/assets/{assetId}/contents", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contents[0].id").value(10))
        .andExpect(jsonPath("$.contents[0].assetId").value(1))
        .andExpect(jsonPath("$.contents[0].sourceType").value("YOUTUBE"))
        .andExpect(jsonPath("$.contents[0].title").value("삼성전자 분석"))
        .andExpect(jsonPath("$.contents[0].url").value("https://youtu.be/example"))
        .andExpect(jsonPath("$.contents[0].imageUrl").value("https://i.ytimg.com/example.jpg"));

    then(assetContentService).should().getContents(1L);
  }
}
