package org.grit.daynomy.person.controller;

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
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse.Success;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateInboxItemResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatePageResponse;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateBulkService;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateInboxService;
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
    controllers = AdminFeaturedPersonPostCandidateInboxController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminFeaturedPersonPostCandidateInboxControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeaturedPersonPostCandidateInboxService inboxService;
  @MockitoBean private FeaturedPersonPostCandidateBulkService bulkService;

  @Test
  void getsFilteredCandidatePage() throws Exception {
    given(inboxService.getPendingCandidates(2, 20, 1L, ContentSourceType.X))
        .willReturn(
            new FeaturedPersonPostCandidatePageResponse(
                List.of(
                    new FeaturedPersonPostCandidateInboxItemResponse(
                        10L,
                        1L,
                        "일론 머스크",
                        null,
                        ContentSourceType.X,
                        "공식 X 글",
                        "https://x.com/elonmusk/status/12345",
                        Instant.parse("2026-10-12T00:00:00Z"),
                        FeaturedPersonPostStatus.PENDING,
                        Instant.parse("2026-10-13T00:00:00Z"))),
                2,
                20,
                21,
                2));

    mockMvc
        .perform(
            get("/api/admin/people/post-candidates")
                .queryParam("page", "2")
                .queryParam("personId", "1")
                .queryParam("sourceType", "X"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[0].personName").value("일론 머스크"))
        .andExpect(jsonPath("$.totalElements").value(21));

    then(inboxService).should().getPendingCandidates(2, 20, 1L, ContentSourceType.X);
  }

  @Test
  void bulkApprovesCandidatesAcrossPeople() throws Exception {
    given(bulkService.approve(List.of(10L, 20L)))
        .willReturn(
            new FeaturedPersonPostCandidateBulkResponse(
                List.of(new Success(10L, null), new Success(20L, null)), List.of()));

    mockMvc
        .perform(
            post("/api/admin/people/post-candidates/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[10,20]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded.length()").value(2));

    then(bulkService).should().approve(List.of(10L, 20L));
  }
}
