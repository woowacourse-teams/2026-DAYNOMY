package org.grit.daynomy.person.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturedPersonPostCandidateBulkServiceTest {

  @Mock private FeaturedPersonPostCollectionService collectionService;
  @InjectMocks private FeaturedPersonPostCandidateBulkService bulkService;

  @Test
  void bulkApproveContinuesAfterOneCandidateFails() {
    FeaturedPersonPostResponse post =
        new FeaturedPersonPostResponse(
            20L,
            ContentSourceType.X,
            "공식 X 글",
            "https://x.com/elonmusk/status/12345",
            Instant.parse("2026-10-12T00:00:00Z"),
            Instant.parse("2026-10-13T00:00:00Z"));
    given(collectionService.approve(1L, 10L)).willReturn(post);
    given(collectionService.approve(1L, 11L))
        .willThrow(
            new BusinessException(
                FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING));

    var response = bulkService.approve(1L, List.of(10L, 11L));

    assertThat(response.succeeded())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.candidateId()).isEqualTo(10L);
              assertThat(item.post()).isEqualTo(post);
            });
    assertThat(response.failed())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.candidateId()).isEqualTo(11L);
              assertThat(item.errorCode())
                  .isEqualTo(
                      FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING.code());
            });
    then(collectionService).should().approve(1L, 10L);
    then(collectionService).should().approve(1L, 11L);
  }

  @Test
  void bulkRejectReturnsEverySuccessfulCandidateId() {
    var response = bulkService.reject(1L, List.of(10L, 11L));

    assertThat(response.succeeded())
        .extracting(item -> item.candidateId())
        .containsExactly(10L, 11L);
    assertThat(response.failed()).isEmpty();
    then(collectionService).should().reject(1L, 10L);
    then(collectionService).should().reject(1L, 11L);
  }

  @Test
  void globalBulkApproveProcessesCandidatesAcrossPeople() {
    FeaturedPersonPostResponse post =
        new FeaturedPersonPostResponse(
            20L,
            ContentSourceType.YOUTUBE,
            "공식 영상",
            "https://www.youtube.com/watch?v=abcdefghijk",
            null,
            Instant.parse("2026-10-13T00:00:00Z"));
    given(collectionService.approve(10L)).willReturn(post);

    var response = bulkService.approve(List.of(10L));

    assertThat(response.succeeded())
        .singleElement()
        .satisfies(item -> assertThat(item.post()).isEqualTo(post));
    then(collectionService).should().approve(10L);
  }

  @Test
  void globalBulkRejectReturnsEverySuccessfulCandidateId() {
    var response = bulkService.reject(List.of(10L, 11L));

    assertThat(response.succeeded())
        .extracting(item -> item.candidateId())
        .containsExactly(10L, 11L);
    then(collectionService).should().reject(10L);
    then(collectionService).should().reject(11L);
  }
}
