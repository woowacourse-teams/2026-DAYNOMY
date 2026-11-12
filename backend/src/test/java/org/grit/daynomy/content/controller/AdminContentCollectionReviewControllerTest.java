package org.grit.daynomy.content.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionRematchResponse;
import org.grit.daynomy.content.dto.ContentCollectionReviewBulkApproveRequest;
import org.grit.daynomy.content.dto.ContentCollectionReviewResponse;
import org.grit.daynomy.content.dto.ContentCollectionReviewUpdateRequest;
import org.grit.daynomy.content.service.ContentCollectionReviewService;
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
    controllers = AdminContentCollectionReviewController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminContentCollectionReviewControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ContentCollectionReviewService reviewService;

  @Test
  void updateChangesPendingContentTranslation() throws Exception {
    given(reviewService.update(3L, new ContentCollectionReviewUpdateRequest("수정 제목", "수정 설명")))
        .willReturn(
            new ContentCollectionReviewResponse(
                3L,
                "경제 채널",
                ContentSourceType.YOUTUBE,
                "수정 제목",
                "수정 설명",
                "https://example.com/1",
                null,
                LocalDate.of(2026, 10, 26),
                List.of(),
                Instant.parse("2026-10-26T01:00:00Z")));

    mockMvc
        .perform(
            put("/api/admin/content-collection-reviews/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정 제목\",\"description\":\"수정 설명\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("수정 제목"))
        .andExpect(jsonPath("$.description").value("수정 설명"));

    then(reviewService)
        .should()
        .update(3L, new ContentCollectionReviewUpdateRequest("수정 제목", "수정 설명"));
  }

  @Test
  void updateRejectsBlankTitle() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/content-collection-reviews/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"description\":\"수정 설명\"}"))
        .andExpect(status().isBadRequest());

    then(reviewService).shouldHaveNoInteractions();
  }

  @Test
  void approvePublishesSelectedAsset() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-reviews/3/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"assetIds\": [], \"industryIds\": [5], \"publishedDate\": \"2026-10-26\"}"))
        .andExpect(status().isNoContent());

    then(reviewService)
        .should()
        .approve(3L, List.of(), List.of(5L), java.time.LocalDate.of(2026, 10, 26));
  }

  @Test
  void approvalRequiresAsset() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-reviews/3/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    then(reviewService).shouldHaveNoInteractions();
  }

  @Test
  void bulkApprovalRequiresAssetsForEveryContent() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-reviews/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"contentId\":3,\"assetIds\":[]}]}"))
        .andExpect(status().isBadRequest());

    then(reviewService).shouldHaveNoInteractions();
  }

  @Test
  void bulkApprovalDelegatesEverySelectedContent() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-reviews/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "items": [
                        {"contentId": 3, "assetIds": [10, 20], "industryIds": [5], "publishedDate": "2026-10-26"},
                        {"contentId": 4, "assetIds": [30], "industryIds": [], "publishedDate": null}
                      ]
                    }
                    """))
        .andExpect(status().isNoContent());

    then(reviewService)
        .should()
        .approveAll(
            List.of(
                new ContentCollectionReviewBulkApproveRequest.Item(
                    3L, List.of(10L, 20L), List.of(5L), java.time.LocalDate.of(2026, 10, 26)),
                new ContentCollectionReviewBulkApproveRequest.Item(
                    4L, List.of(30L), List.of(), null)));
  }

  @Test
  void bulkRejectionDelegatesSelectedContentIds() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/content-collection-reviews/bulk-reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentIds\":[3,4]}"))
        .andExpect(status().isNoContent());

    then(reviewService).should().rejectAll(List.of(3L, 4L));
  }

  @Test
  void rematchReturnsProcessingCounts() throws Exception {
    org.mockito.BDDMockito.given(reviewService.rematchPending())
        .willReturn(new ContentCollectionRematchResponse(5, 3, 7));

    mockMvc
        .perform(post("/api/admin/content-collection-reviews/rematch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processedCount").value(5))
        .andExpect(jsonPath("$.matchedCount").value(3))
        .andExpect(jsonPath("$.suggestionCount").value(7));
  }
}
