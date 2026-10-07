package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.AssetContentRequest;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;
import org.grit.daynomy.news.domain.News;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AssetContentServiceTest {

  @Mock private AssetRepository assetRepository;
  @Mock private AssetContentRepository contentRepository;
  @Mock private YouTubeClient youtubeClient;
  @InjectMocks private AssetContentService assetContentService;

  @Test
  @DisplayName("종목 관련 자료 조회는 등록된 자료를 반환한다")
  void getContents() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(1L);
    given(asset.getAssetCode()).willReturn("005930");
    given(asset.getName()).willReturn("삼성전자");
    AssetContent content =
        AssetContent.create(
            asset, ContentSourceType.YOUTUBE, "삼성전자 분석", "https://youtu.be/example");
    given(assetRepository.findById(1L)).willReturn(Optional.of(asset));
    given(contentRepository.findAllByAssetIdOrderByCreatedAtDescIdDesc(1L))
        .willReturn(List.of(content));

    var response = assetContentService.getContents(1L);

    assertThat(response.contents()).hasSize(1);
    assertThat(response.assetCode()).isEqualTo("005930");
    assertThat(response.assetName()).isEqualTo("삼성전자");
    assertThat(response.contents().getFirst().title()).isEqualTo("삼성전자 분석");
    then(contentRepository).should().findAllByAssetIdOrderByCreatedAtDescIdDesc(1L);
  }

  @Test
  @DisplayName("종목 관련 자료 등록은 같은 URL의 중복 등록을 거부한다")
  void createContentRejectsDuplicateUrl() {
    Asset asset = mock(Asset.class);
    given(assetRepository.findById(1L)).willReturn(Optional.of(asset));
    given(contentRepository.existsByAssetIdAndUrl(1L, "https://example.com/news")).willReturn(true);

    var request =
        new AssetContentRequest(
            ContentSourceType.INTERNAL_NEWS, "내부 뉴스", "https://example.com/news", null);

    assertThatThrownBy(() -> assetContentService.createContent(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ContentErrorCode.ASSET_CONTENT_ALREADY_EXISTS);

    then(contentRepository).should().existsByAssetIdAndUrl(1L, "https://example.com/news");
    then(contentRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName("YouTube 검색은 종목을 확인한 뒤 검색 결과를 반환한다")
  void searchYouTube() {
    Asset asset = mock(Asset.class);
    given(assetRepository.findById(1L)).willReturn(Optional.of(asset));
    given(youtubeClient.search("삼성전자 005930"))
        .willReturn(
            List.of(
                new YouTubeVideoCandidate(
                    "삼성전자 분석", "https://www.youtube.com/watch?v=abc", "채널", "2026-10-04", "")));

    var response = assetContentService.searchYouTube(1L, "삼성전자 005930");

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().title()).isEqualTo("삼성전자 분석");
    assertThat(response.items().getFirst().url()).isEqualTo("https://www.youtube.com/watch?v=abc");
    then(youtubeClient).should().search("삼성전자 005930");
  }

  @Test
  @DisplayName("뉴스 관련 종목 동기화는 기존 연결을 flush한 뒤 새 연결을 저장한다")
  void syncNewsContentsFlushesBeforeSaving() {
    News news = mock(News.class);
    Asset asset = mock(Asset.class);
    given(news.getId()).willReturn(1L);
    given(assetRepository.findAllById(List.of(1L))).willReturn(List.of(asset));

    assetContentService.syncNewsContents(news, List.of(1L));

    InOrder inOrder = inOrder(contentRepository);
    inOrder.verify(contentRepository).deleteAllByNewsId(1L);
    inOrder.verify(contentRepository).flush();
    inOrder.verify(contentRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("동시 등록으로 URL 유니크 제약조건 위반 시 중복 오류로 변환한다")
  void createContentMapsUrlUniqueConstraintViolation() {
    Asset asset = mock(Asset.class);
    ConstraintViolationException constraintViolation = mock(ConstraintViolationException.class);
    DataIntegrityViolationException exception =
        new DataIntegrityViolationException("duplicate content", constraintViolation);
    given(assetRepository.findById(1L)).willReturn(Optional.of(asset));
    given(contentRepository.existsByAssetIdAndUrl(1L, "https://example.com/news"))
        .willReturn(false);
    given(constraintViolation.getConstraintName())
        .willReturn("uk_stock_related_contents_asset_url");
    given(contentRepository.saveAndFlush(any(AssetContent.class))).willThrow(exception);

    var request =
        new AssetContentRequest(
            ContentSourceType.INTERNAL_NEWS, "내부 뉴스", "https://example.com/news", null);

    assertThatThrownBy(() -> assetContentService.createContent(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ContentErrorCode.ASSET_CONTENT_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("다른 데이터 무결성 예외는 중복 오류로 변환하지 않는다")
  void createContentRethrowsOtherDataIntegrityViolation() {
    Asset asset = mock(Asset.class);
    DataIntegrityViolationException exception =
        new DataIntegrityViolationException("other constraint");
    given(assetRepository.findById(1L)).willReturn(Optional.of(asset));
    given(contentRepository.existsByAssetIdAndUrl(1L, "https://example.com/news"))
        .willReturn(false);
    given(contentRepository.saveAndFlush(any(AssetContent.class))).willThrow(exception);

    var request =
        new AssetContentRequest(
            ContentSourceType.INTERNAL_NEWS, "내부 뉴스", "https://example.com/news", null);

    assertThatThrownBy(() -> assetContentService.createContent(1L, request)).isSameAs(exception);
  }
}
