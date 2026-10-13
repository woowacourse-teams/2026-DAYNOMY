package org.grit.daynomy.person.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.external.wikimedia.WikimediaImageCandidate;
import org.grit.daynomy.external.wikimedia.WikimediaImageClient;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.dto.FeaturedPersonImageSelectionRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostRequest;
import org.grit.daynomy.person.dto.FeaturedPersonRequest;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturedPersonServiceTest {

  @Mock private FeaturedPersonRepository personRepository;
  @Mock private FeaturedPersonPostRepository postRepository;
  @Mock private OpenAiSocialPostSearchClient socialPostSearchClient;
  @Mock private WikimediaImageClient wikimediaImageClient;
  @Mock private S3ImageStorage s3ImageStorage;
  @InjectMocks private FeaturedPersonService featuredPersonService;

  @Test
  @DisplayName("주요 인물 등록은 공식 프로필 URL을 정규화한다")
  void createPersonNormalizesProfileUrls() {
    given(personRepository.existsByName("일론 머스크")).willReturn(false);
    given(personRepository.saveAndFlush(any(FeaturedPerson.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var response =
        featuredPersonService.createPerson(
            new FeaturedPersonRequest(
                " 일론 머스크 ",
                "https://X.com/elonmusk/?lang=ko",
                "https://www.threads.net/@elonmusk/",
                "https://www.youtube.com/@Tesla/",
                null));

    assertThat(response.name()).isEqualTo("일론 머스크");
    assertThat(response.xProfileUrl()).isEqualTo("https://x.com/elonmusk");
    assertThat(response.threadsProfileUrl()).isEqualTo("https://www.threads.net/@elonmusk");
    assertThat(response.youtubeProfileUrl()).isEqualTo("https://www.youtube.com/@Tesla");
  }

  @Test
  @DisplayName("주요 인물 게시물 검색은 이름과 등록된 프로필 URL을 사용한다")
  void searchPostsUsesPersonAndProfileUrls() {
    FeaturedPerson person = mock(FeaturedPerson.class);
    given(person.getName()).willReturn("일론 머스크");
    given(person.getXProfileUrl()).willReturn("https://x.com/elonmusk");
    given(person.getThreadsProfileUrl()).willReturn("https://www.threads.net/@elonmusk");
    given(personRepository.findById(1L)).willReturn(Optional.of(person));
    given(
            socialPostSearchClient.search(
                "일론 머스크 https://x.com/elonmusk https://www.threads.net/@elonmusk"))
        .willReturn(
            List.of(
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X,
                    "일론 머스크 게시물",
                    "https://x.com/elonmusk/status/12345",
                    "")));

    var response = featuredPersonService.searchPosts(1L);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().sourceType()).isEqualTo(ContentSourceType.X);
    then(socialPostSearchClient)
        .should()
        .search("일론 머스크 https://x.com/elonmusk https://www.threads.net/@elonmusk");
  }

  @Test
  @DisplayName("게시물 등록은 빈 제목을 인물과 플랫폼 이름으로 채운다")
  void createPostUsesDefaultTitle() {
    FeaturedPerson person = mock(FeaturedPerson.class);
    given(person.getName()).willReturn("일론 머스크");
    given(personRepository.findById(1L)).willReturn(Optional.of(person));
    given(postRepository.existsByPersonIdAndUrl(1L, "https://x.com/elonmusk/status/12345"))
        .willReturn(false);
    given(postRepository.saveAndFlush(any(FeaturedPersonPost.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var response =
        featuredPersonService.createPost(
            1L,
            new FeaturedPersonPostRequest(
                ContentSourceType.X, " ", "https://x.com/elonmusk/status/12345?ref=home", null));

    assertThat(response.title()).isEqualTo("일론 머스크 X 게시물");
    assertThat(response.url()).isEqualTo("https://x.com/elonmusk/status/12345");
  }

  @Test
  @DisplayName("선택한 플랫폼과 게시물 URL이 다르면 등록을 거부한다")
  void createPostRejectsMismatchedPlatformUrl() {
    given(personRepository.findById(1L)).willReturn(Optional.of(mock(FeaturedPerson.class)));

    assertThatThrownBy(
            () ->
                featuredPersonService.createPost(
                    1L,
                    new FeaturedPersonPostRequest(
                        ContentSourceType.THREADS, null, "https://x.com/elonmusk/status/12345", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);

    then(postRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("YouTube 영상 URL을 등록하고 기본 제목을 채운다")
  void createPostAcceptsYouTubeVideo() {
    FeaturedPerson person = mock(FeaturedPerson.class);
    given(person.getName()).willReturn("일론 머스크");
    given(personRepository.findById(1L)).willReturn(Optional.of(person));
    given(postRepository.existsByPersonIdAndUrl(1L, "https://youtu.be/dQw4w9WgXcQ"))
        .willReturn(false);
    given(postRepository.saveAndFlush(any(FeaturedPersonPost.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var response =
        featuredPersonService.createPost(
            1L,
            new FeaturedPersonPostRequest(
                ContentSourceType.YOUTUBE, null, "https://youtu.be/dQw4w9WgXcQ?t=1", null));

    assertThat(response.title()).isEqualTo("일론 머스크 YouTube 게시물");
    assertThat(response.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
  }

  @Test
  @DisplayName("Wikimedia Commons 프로필 이미지는 내려받아 S3에 저장한다")
  void createPersonStoresSelectedWikimediaImage() {
    given(personRepository.existsByName("일론 머스크")).willReturn(false);
    given(personRepository.saveAndFlush(any(FeaturedPerson.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(wikimediaImageClient.download("File:Elon Musk.jpg"))
        .willReturn(
            new WikimediaImageClient.ImportedImage(
                new WikimediaImageCandidate(
                    "File:Elon Musk.jpg",
                    "https://thumb.example.com/elon.jpg",
                    "",
                    "",
                    "",
                    "",
                    100,
                    100),
                new byte[] {1, 2, 3},
                "image/jpeg",
                "jpg"));
    given(s3ImageStorage.upload(any(), any(), any()))
        .willReturn(
            new S3ImageStorage.StoredImage("elon.jpg", "https://images.example.com/elon.jpg"));

    var response =
        featuredPersonService.createPerson(
            new FeaturedPersonRequest(
                "일론 머스크",
                null,
                null,
                null,
                new FeaturedPersonImageSelectionRequest("File:Elon Musk.jpg")));

    assertThat(response.profileImageUrl()).isEqualTo("https://images.example.com/elon.jpg");
    then(wikimediaImageClient).should().download("File:Elon Musk.jpg");
  }
}
