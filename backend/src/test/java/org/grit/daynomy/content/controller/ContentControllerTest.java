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
import org.grit.daynomy.content.dto.ContentPageResponse;
import org.grit.daynomy.content.dto.DiscoverableAssetsResponse;
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
    controllers = ContentController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ContentControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private AssetContentService service;

  @Test
  void returnsPublicContentsAndPagination() throws Exception {
    given(service.getPublicContents(2, 12))
        .willReturn(
            new ContentPageResponse(
                List.of(
                    new AssetContentResponse(
                        1L,
                        2L,
                        ContentSourceType.YOUTUBE,
                        "시장 소식",
                        "https://youtu.be/abcdefghijk",
                        null,
                        null,
                        null)),
                2,
                12,
                3,
                25,
                true));
    mockMvc
        .perform(get("/api/contents").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].sourceType").value("YOUTUBE"))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.hasNext").value(true));
  }

  @Test
  void rejectsInvalidPagination() throws Exception {
    mockMvc.perform(get("/api/contents").param("page", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/contents").param("size", "101")).andExpect(status().isBadRequest());
    then(service).shouldHaveNoInteractions();
  }

  @Test
  void returnsOnlyDiscoverableAssetIds() throws Exception {
    given(service.getDiscoverableAssets(2, 1))
        .willReturn(new DiscoverableAssetsResponse(List.of(12L), false));

    mockMvc
        .perform(get("/api/contents/assets").param("page", "2").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetIds[0]").value(12))
        .andExpect(jsonPath("$.hasNext").value(false));
    mockMvc
        .perform(get("/api/contents/assets").param("size", "101"))
        .andExpect(status().isBadRequest());
  }
}
