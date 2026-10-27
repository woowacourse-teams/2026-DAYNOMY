package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.repository.CollectedExternalContentRepository;
import org.grit.daynomy.content.repository.CollectedExternalContentReviewRepository;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentCollectionPersistenceServiceTest {

  @Mock private ContentCollectionSourceRepository sourceRepository;
  @Mock private CollectedExternalContentRepository collectedContentRepository;
  @Mock private CollectedExternalContentReviewRepository reviewRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetMentionMatcher assetMentionMatcher;

  @Test
  void savesNewContentAndLinksConfiguredAsset() {
    Asset asset = new Asset("삼성전자", AssetCategory.STOCK, "005930");
    ReflectionTestUtils.setField(asset, "id", 10L);
    ContentCollectionSource source =
        new ContentCollectionSource(
            "삼성전자 공식 X", ContentSourceType.X, "https://x.com/samsung", asset, true);
    ReflectionTestUtils.setField(source, "id", 1L);
    CollectedContentItem item =
        new CollectedContentItem(
            "신제품 공개", "", "https://x.com/samsung/status/123", null, LocalDate.of(2026, 10, 26));
    Instant collectedAt = Instant.parse("2026-10-26T01:00:00Z");
    given(sourceRepository.findById(1L)).willReturn(Optional.of(source));
    given(collectedContentRepository.findByUrl(item.url())).willReturn(Optional.empty());
    given(collectedContentRepository.save(org.mockito.ArgumentMatchers.any()))
        .willAnswer(
            invocation -> {
              CollectedExternalContent content = invocation.getArgument(0);
              ReflectionTestUtils.setField(content, "id", 20L);
              return content;
            });
    given(reviewRepository.existsByCollectedContentIdAndSuggestedAssetId(20L, 10L))
        .willReturn(false);
    ContentCollectionPersistenceService service = service();

    var result = service.persist(1L, List.of(item), collectedAt);

    assertThat(result.savedCount()).isEqualTo(1);
    assertThat(result.linkedCount()).isEqualTo(1);
    assertThat(source.getLastCollectedAt()).isEqualTo(collectedAt);
    ArgumentCaptor<CollectedExternalContent> collectedCaptor =
        ArgumentCaptor.forClass(CollectedExternalContent.class);
    then(collectedContentRepository).should().save(collectedCaptor.capture());
    assertThat(collectedCaptor.getValue().getUrl()).isEqualTo(item.url());
    ArgumentCaptor<CollectedExternalContentReview> reviewCaptor =
        ArgumentCaptor.forClass(CollectedExternalContentReview.class);
    then(reviewRepository).should().save(reviewCaptor.capture());
    assertThat(reviewCaptor.getValue().getSuggestedAsset()).isEqualTo(asset);
  }

  @Test
  void duplicateOriginalCanStillBeLinkedToNewAsset() {
    Asset asset = new Asset("삼성전자", AssetCategory.STOCK, "005930");
    ReflectionTestUtils.setField(asset, "id", 10L);
    ContentCollectionSource source =
        new ContentCollectionSource(
            "경제 채널", ContentSourceType.YOUTUBE, "https://youtube.com/@economy", null, true);
    ReflectionTestUtils.setField(source, "id", 1L);
    CollectedContentItem item =
        new CollectedContentItem(
            "삼성전자 실적", "", "https://www.youtube.com/watch?v=abc", null, LocalDate.now());
    given(sourceRepository.findById(1L)).willReturn(Optional.of(source));
    CollectedExternalContent collectedContent =
        new CollectedExternalContent(source, item.title(), "", item.url(), null, item.publishedDate());
    ReflectionTestUtils.setField(collectedContent, "id", 20L);
    given(collectedContentRepository.findByUrl(item.url())).willReturn(Optional.of(collectedContent));
    given(assetRepository.findAllByCategoryInAndListedTrue(org.mockito.ArgumentMatchers.any()))
        .willReturn(List.of(asset));
    given(assetMentionMatcher.match(item.title(), item.description(), List.of(asset)))
        .willReturn(List.of(asset));
    given(reviewRepository.existsByCollectedContentIdAndSuggestedAssetId(20L, 10L))
        .willReturn(false);

    var result = service().persist(1L, List.of(item), Instant.now());

    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.linkedCount()).isEqualTo(1);
    then(reviewRepository).should().save(org.mockito.ArgumentMatchers.any());
  }

  private ContentCollectionPersistenceService service() {
    return new ContentCollectionPersistenceService(
        sourceRepository,
        collectedContentRepository,
        reviewRepository,
        assetRepository,
        assetMentionMatcher);
  }
}
