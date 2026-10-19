package org.grit.daynomy.person.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturedPersonPostCollectionServiceTest {

  @Mock private FeaturedPersonRepository personRepository;
  @Mock private FeaturedPersonPostRepository postRepository;
  @Mock private OpenAiSocialPostSearchClient socialPostSearchClient;
  @Mock private YouTubeClient youtubeClient;
  @Mock private FeaturedPersonService featuredPersonService;
  @InjectMocks private FeaturedPersonPostCollectionService collectionService;

  @Test
  void collectStoresOnlyNewCandidatesFromConfiguredProfiles() {
    FeaturedPerson person = org.mockito.Mockito.mock(FeaturedPerson.class);
    given(person.getXProfileUrl()).willReturn("https://x.com/elonmusk");
    given(person.getThreadsProfileUrl()).willReturn("https://threads.net/@elonmusk");
    given(person.getYoutubeProfileUrl()).willReturn("https://youtube.com/@Tesla");
    given(personRepository.findById(1L)).willReturn(Optional.of(person));
    given(
            socialPostSearchClient.searchProfiles(
                "https://x.com/elonmusk",
                "https://threads.net/@elonmusk",
                LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-12")))
        .willReturn(
            List.of(
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X,
                    "공식 X 글",
                    "https://x.com/elonmusk/status/12345",
                    "2026-10-12")));
    given(
            youtubeClient.searchProfile(
                "https://youtube.com/@Tesla",
                Instant.parse("2026-10-09T15:00:00Z"),
                Instant.parse("2026-10-12T15:00:00Z")))
        .willReturn(
            List.of(
                new YouTubeVideoCandidate(
                    "공식 영상",
                    "https://www.youtube.com/watch?v=abcdefghijk",
                    "Tesla",
                    "2026-10-12T03:00:00Z",
                    "")));
    given(featuredPersonService.normalizePostUrl(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(postRepository.findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(any(), any()))
        .willReturn(List.of());

    collectionService.collect(1L, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12"));

    ArgumentCaptor<FeaturedPersonPost> captor = ArgumentCaptor.forClass(FeaturedPersonPost.class);
    then(postRepository).should(org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(FeaturedPersonPost::getSourceType)
        .containsExactly(ContentSourceType.X, ContentSourceType.YOUTUBE);
    assertThat(captor.getAllValues().get(0).getPublishedAt())
        .isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
    assertThat(captor.getAllValues().get(1).getPublishedAt())
        .isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
  }

  @Test
  void collectRejectsPeriodsLongerThanThirtyOneDays() {
    assertThatThrownBy(
            () ->
                collectionService.collect(
                    1L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-02")))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(FeaturedPersonErrorCode.FEATURED_PERSON_POST_COLLECTION_PERIOD_INVALID);

    then(personRepository).shouldHaveNoInteractions();
  }

  @Test
  void collectSkipsOnlyCandidatesWithInvalidUrls() {
    FeaturedPerson person = org.mockito.Mockito.mock(FeaturedPerson.class);
    given(person.getXProfileUrl()).willReturn("https://x.com/elonmusk");
    given(personRepository.findById(1L)).willReturn(Optional.of(person));
    given(
            socialPostSearchClient.searchProfiles(
                "https://x.com/elonmusk",
                null,
                LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-12")))
        .willReturn(
            List.of(
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X,
                    "유효한 글",
                    "https://x.com/elonmusk/status/12345",
                    "2026-10-12"),
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X,
                    "잘못된 글",
                    "https://x.com/elonmusk/not-a-post",
                    "2026-10-12")));
    given(
            featuredPersonService.normalizePostUrl(
                "https://x.com/elonmusk/status/12345", ContentSourceType.X))
        .willReturn("https://x.com/elonmusk/status/12345");
    given(
            featuredPersonService.normalizePostUrl(
                "https://x.com/elonmusk/not-a-post", ContentSourceType.X))
        .willThrow(
            new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL));
    given(postRepository.findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(any(), any()))
        .willReturn(List.of());

    collectionService.collect(1L, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12"));

    then(postRepository).should().save(any(FeaturedPersonPost.class));
  }

  @Test
  void approveMarksPostApproved() {
    FeaturedPersonPost candidate = org.mockito.Mockito.mock(FeaturedPersonPost.class);
    given(candidate.getStatus()).willReturn(FeaturedPersonPostStatus.PENDING);
    given(candidate.getId()).willReturn(10L);
    given(candidate.getSourceType()).willReturn(ContentSourceType.X);
    given(candidate.getTitle()).willReturn("공식 X 글");
    given(candidate.getUrl()).willReturn("https://x.com/elonmusk/status/12345");
    given(candidate.getPublishedAt()).willReturn(Instant.parse("2026-10-12T00:00:00Z"));
    given(candidate.getCreatedAt()).willReturn(Instant.parse("2026-10-13T00:00:00Z"));
    given(postRepository.findByIdAndPersonId(10L, 1L)).willReturn(Optional.of(candidate));
    FeaturedPersonPostResponse expected =
        new FeaturedPersonPostResponse(
            10L,
            ContentSourceType.X,
            "공식 X 글",
            "https://x.com/elonmusk/status/12345",
            Instant.parse("2026-10-12T00:00:00Z"),
            Instant.parse("2026-10-13T00:00:00Z"));

    var response = collectionService.approve(1L, 10L);

    assertThat(response).isEqualTo(expected);
    then(candidate).should().approve(any(Instant.class));
  }

  @Test
  void globalApproveMarksPostApproved() {
    FeaturedPersonPost candidate = org.mockito.Mockito.mock(FeaturedPersonPost.class);
    given(candidate.getStatus()).willReturn(FeaturedPersonPostStatus.PENDING);
    given(candidate.getId()).willReturn(10L);
    given(candidate.getSourceType()).willReturn(ContentSourceType.X);
    given(candidate.getTitle()).willReturn("공식 X 글");
    given(candidate.getUrl()).willReturn("https://x.com/example/status/12345");
    given(candidate.getCreatedAt()).willReturn(Instant.parse("2026-10-13T00:00:00Z"));
    given(postRepository.findById(10L)).willReturn(Optional.of(candidate));
    FeaturedPersonPostResponse expected =
        new FeaturedPersonPostResponse(
            10L,
            ContentSourceType.X,
            "공식 X 글",
            "https://x.com/example/status/12345",
            null,
            Instant.parse("2026-10-13T00:00:00Z"));

    var response = collectionService.approve(10L);

    assertThat(response).isEqualTo(expected);
    then(candidate).should().approve(any(Instant.class));
  }
}
