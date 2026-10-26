package org.grit.daynomy.person.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse.Failure;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse.Success;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatesResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.service.FeaturedPersonPostCandidateBulkService;
import org.grit.daynomy.person.service.FeaturedPersonPostCollectionService;
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
    controllers = AdminFeaturedPersonPostCandidateController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminFeaturedPersonPostCandidateControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeaturedPersonPostCollectionService collectionService;
  @MockitoBean private FeaturedPersonPostCandidateBulkService bulkService;

  @Test
  void getsPendingCandidatesAndApprovesOne() throws Exception {
    Instant publishedAt = Instant.parse("2026-10-12T00:00:00Z");
    given(collectionService.getPendingCandidates(1L))
        .willReturn(
            new FeaturedPersonPostCandidatesResponse(
                List.of(
                    new FeaturedPersonPostCandidateResponse(
                        10L,
                        ContentSourceType.X,
                        "공식 X 글",
                        "https://x.com/elonmusk/status/12345",
                        publishedAt,
                        FeaturedPersonPostStatus.PENDING,
                        Instant.parse("2026-10-13T00:00:00Z")))));
    given(collectionService.approve(1L, 10L))
        .willReturn(
            new FeaturedPersonPostResponse(
                20L,
                ContentSourceType.X,
                "공식 X 글",
                "https://x.com/elonmusk/status/12345",
                publishedAt,
                Instant.parse("2026-10-13T00:00:00Z")));

    mockMvc
        .perform(get("/api/admin/people/{personId}/post-candidates", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[0].status").value("PENDING"));
    mockMvc
        .perform(
            post("/api/admin/people/{personId}/post-candidates/{candidateId}/approve", 1L, 10L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(20));
    mockMvc
        .perform(post("/api/admin/people/{personId}/post-candidates/{candidateId}/reject", 1L, 11L))
        .andExpect(status().isNoContent());

    then(collectionService).should().getPendingCandidates(1L);
    then(collectionService).should().approve(1L, 10L);
    then(collectionService).should().reject(1L, 11L);
  }

  @Test
  void collectsCandidatesForRequestedPeriod() throws Exception {
    given(
            collectionService.collect(
                1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12")))
        .willReturn(new FeaturedPersonPostCandidatesResponse(List.of()));

    mockMvc
        .perform(
            post("/api/admin/people/{personId}/post-candidates/collect", 1L)
                .queryParam("from", "2026-10-01")
                .queryParam("to", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates").isArray());

    then(collectionService)
        .should()
        .collect(1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12"));
  }

  @Test
  void bulkApprovesSelectedCandidatesAndReturnsPartialFailures() throws Exception {
    FeaturedPersonPostResponse post =
        new FeaturedPersonPostResponse(
            20L,
            ContentSourceType.X,
            "공식 X 글",
            "https://x.com/elonmusk/status/12345",
            Instant.parse("2026-10-12T00:00:00Z"),
            Instant.parse("2026-10-13T00:00:00Z"));
    given(bulkService.approve(1L, List.of(10L, 11L)))
        .willReturn(
            new FeaturedPersonPostCandidateBulkResponse(
                List.of(new Success(10L, post)),
                List.of(new Failure(11L, "FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING", "실패"))));

    mockMvc
        .perform(
            post("/api/admin/people/{personId}/post-candidates/bulk-approve", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[10,11]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded[0].candidateId").value(10))
        .andExpect(jsonPath("$.succeeded[0].post.id").value(20))
        .andExpect(jsonPath("$.failed[0].candidateId").value(11));

    then(bulkService).should().approve(1L, List.of(10L, 11L));
  }

  @Test
  void bulkRejectRequiresAtLeastOneCandidate() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/people/{personId}/post-candidates/bulk-reject", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[]}"))
        .andExpect(status().isBadRequest());

    then(bulkService).shouldHaveNoInteractions();
  }
}
