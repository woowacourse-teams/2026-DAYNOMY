package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionManualRequest;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentCollectionManualServiceTest {

  @Mock private ContentCollectionSourceRepository sourceRepository;
  @Mock private OpenAiSocialPostSearchClient socialPostSearchClient;
  @Mock private ContentCollectionPersistenceService persistenceService;

  @Test
  void registersVerifiedXPostForAutomaticAssetMatching() {
    ContentCollectionSource source =
        new ContentCollectionSource(
            "한국경제신문", ContentSourceType.X, "https://x.com/hankyungmedia", null, true);
    ContentCollectionManualRequest request =
        new ContentCollectionManualRequest(
            "https://x.com/hankyungmedia/status/123?s=20", null, null, null);
    SocialPostCandidate candidate =
        new SocialPostCandidate(
            SocialPostCandidate.Platform.X,
            "삼성전자와 SK하이닉스 투자 전망",
            "https://x.com/hankyungmedia/status/123",
            "2026-10-27");
    given(sourceRepository.findById(1L)).willReturn(Optional.of(source));
    given(
            socialPostSearchClient.resolvePost(
                request.url(), "한국경제신문 X 게시물", request.publishedDate()))
        .willReturn(Optional.of(candidate));
    given(
            persistenceService.persistManual(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
        .willReturn(new ContentCollectionResultResponse(1L, 1, 1, 0, 2));

    var result = service().register(1L, request);

    assertThat(result.linkedCount()).isEqualTo(2);
    ArgumentCaptor<CollectedContentItem> item = ArgumentCaptor.forClass(CollectedContentItem.class);
    then(persistenceService)
        .should()
        .persistManual(org.mockito.ArgumentMatchers.eq(1L), item.capture());
    assertThat(item.getValue().title()).contains("삼성전자", "SK하이닉스");
    assertThat(item.getValue().publishedDate()).isEqualTo(LocalDate.of(2026, 10, 27));
  }

  @Test
  void rejectsPostFromDifferentAccount() {
    ContentCollectionSource source =
        new ContentCollectionSource(
            "한국경제신문", ContentSourceType.X, "https://x.com/hankyungmedia", null, true);
    ContentCollectionManualRequest request =
        new ContentCollectionManualRequest("https://x.com/other/status/123", null, null, null);
    given(sourceRepository.findById(1L)).willReturn(Optional.of(source));
    given(
            socialPostSearchClient.resolvePost(
                request.url(), "한국경제신문 X 게시물", request.publishedDate()))
        .willReturn(
            Optional.of(
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X, "게시물", "https://x.com/other/status/123", "")));

    assertThatThrownBy(() -> service().register(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ContentErrorCode.CONTENT_COLLECTION_POST_INVALID);
    then(persistenceService).shouldHaveNoInteractions();
  }

  private ContentCollectionManualService service() {
    return new ContentCollectionManualService(
        sourceRepository, socialPostSearchClient, persistenceService);
  }
}
