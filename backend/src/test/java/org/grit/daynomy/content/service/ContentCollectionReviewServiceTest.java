package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionReviewStatus;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentCollectionReviewServiceTest {

  @Mock private CollectedExternalContentRepository collectedContentRepository;
  @Mock private CollectedExternalContentReviewRepository reviewRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetContentRepository assetContentRepository;
  @Mock private AssetMentionMatcher assetMentionMatcher;

  @Test
  void approvalPublishesContentForEverySelectedAsset() {
    Asset samsung = asset(1L, "삼성전자", "005930");
    Asset hynix = asset(2L, "SK하이닉스", "000660");
    CollectedExternalContent content = content("삼성전자와 SK하이닉스 반도체 전망");
    ReflectionTestUtils.setField(content, "id", 3L);
    ReflectionTestUtils.setField(content, "publishedDate", null);
    CollectedExternalContentReview samsungReview =
        new CollectedExternalContentReview(content, samsung);
    CollectedExternalContentReview hynixReview = new CollectedExternalContentReview(content, hynix);
    given(
            reviewRepository.findAllByCollectedContentIdAndStatusOrderByIdAsc(
                3L, ContentCollectionReviewStatus.PENDING))
        .willReturn(List.of(samsungReview, hynixReview));
    given(assetRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(samsung, hynix));
    given(assetContentRepository.existsByAssetIdAndUrl(any(), any())).willReturn(false);

    service().approve(3L, List.of(1L, 2L), LocalDate.of(2026, 10, 26));

    assertThat(samsungReview.getStatus()).isEqualTo(ContentCollectionReviewStatus.APPROVED);
    assertThat(hynixReview.getStatus()).isEqualTo(ContentCollectionReviewStatus.APPROVED);
    assertThat(content.getPublishedDate()).isEqualTo(LocalDate.of(2026, 10, 26));
    ArgumentCaptor<AssetContent> published = ArgumentCaptor.forClass(AssetContent.class);
    then(assetContentRepository).should(org.mockito.Mockito.times(2)).save(published.capture());
    assertThat(published.getAllValues())
        .extracting(contentItem -> contentItem.getAsset().getId())
        .containsExactly(1L, 2L);
    assertThat(published.getAllValues())
        .extracting(AssetContent::getPublishedDate)
        .containsOnly(LocalDate.of(2026, 10, 26));
  }

  @Test
  void rejectionClosesEveryCandidateForTheContent() {
    CollectedExternalContent content = content("시장 전망");
    CollectedExternalContentReview first = new CollectedExternalContentReview(content, null);
    CollectedExternalContentReview second =
        new CollectedExternalContentReview(content, asset(2L, "SK하이닉스", "000660"));
    given(
            reviewRepository.findAllByCollectedContentIdAndStatusOrderByIdAsc(
                3L, ContentCollectionReviewStatus.PENDING))
        .willReturn(List.of(first, second));

    service().reject(3L);

    assertThat(first.getStatus()).isEqualTo(ContentCollectionReviewStatus.REJECTED);
    assertThat(second.getStatus()).isEqualTo(ContentCollectionReviewStatus.REJECTED);
    then(assetContentRepository).shouldHaveNoInteractions();
  }

  @Test
  void rematchReplacesUnmatchedCandidateWithEveryMentionedAsset() {
    Asset samsung = asset(1L, "삼성전자", "005930");
    Asset hynix = asset(2L, "SK하이닉스", "000660");
    CollectedExternalContent content = content("삼성전자와 SK하이닉스 실적");
    ReflectionTestUtils.setField(content, "id", 3L);
    CollectedExternalContentReview unmatched = new CollectedExternalContentReview(content, null);
    given(
            reviewRepository.findAllByStatusAndSuggestedAssetIsNullOrderByIdAsc(
                ContentCollectionReviewStatus.PENDING))
        .willReturn(List.of(unmatched));
    given(assetRepository.findAllByCategoryInAndListedTrue(any()))
        .willReturn(List.of(samsung, hynix));
    given(
            assetMentionMatcher.match(
                content.getTitle(), content.getDescription(), List.of(samsung, hynix)))
        .willReturn(List.of(samsung, hynix));

    var result = service().rematchPending();

    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(result.matchedCount()).isEqualTo(1);
    assertThat(result.suggestionCount()).isEqualTo(2);
    assertThat(unmatched.getStatus()).isEqualTo(ContentCollectionReviewStatus.REJECTED);
    then(reviewRepository).should(org.mockito.Mockito.times(2)).save(any());
  }

  private CollectedExternalContent content(String title) {
    return new CollectedExternalContent(
        new ContentCollectionSource(
            "경제 채널", ContentSourceType.YOUTUBE, "https://youtube.com/@economy", null, true),
        title,
        "",
        "https://youtube.com/watch?v=abc",
        null,
        LocalDate.of(2026, 10, 27));
  }

  private Asset asset(Long id, String name, String code) {
    Asset asset = new Asset(name, AssetCategory.STOCK, code);
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private ContentCollectionReviewService service() {
    return new ContentCollectionReviewService(
        collectedContentRepository,
        reviewRepository,
        assetRepository,
        assetContentRepository,
        assetMentionMatcher);
  }
}
