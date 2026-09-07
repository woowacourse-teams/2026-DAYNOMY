package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminNewsServiceTest {

  @Mock private NewsRepository newsRepository;

  @Mock private S3ImageStorage s3ImageStorage;

  @Mock private KeywordAiClient keywordAiClient;

  @Mock private MarketAnalysisAiClient marketAnalysisAiClient;

  @Mock private KeywordService keywordService;

  @Mock private MarketAnalysisService marketAnalysisService;

  @InjectMocks private AdminNewsService adminNewsService;

  @Test
  @DisplayName("관리자 뉴스 등록은 수동 출처의 초안으로 저장한다")
  void createNewsSavesManualDraft() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest("뉴스 제목", "뉴스 본문", "https://example.com/news/1", Category.STOCK);
    MockMultipartFile image =
        new MockMultipartFile("image", "news.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});
    given(s3ImageStorage.upload(any(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE)))
        .willReturn(
            new S3ImageStorage.StoredImage("news-image.png", "https://example.com/news-image.png"));
    given(newsRepository.save(any(News.class))).willAnswer(invocation -> invocation.getArgument(0));

    News savedNews = adminNewsService.createDraft(request, image);

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    News capturedNews = newsCaptor.getValue();
    assertThat(savedNews).isSameAs(capturedNews);
    assertThat(capturedNews.getTitle()).isEqualTo("뉴스 제목");
    assertThat(capturedNews.getContent()).isEqualTo("뉴스 본문");
    assertThat(capturedNews.getImageUrl()).isEqualTo("https://example.com/news-image.png");
    assertThat(capturedNews.getSource()).isNull();
    assertThat(capturedNews.getExternalId()).isNull();
    assertThat(capturedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
    assertThat(capturedNews.getPublishedAt()).isNull();
    ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(s3ImageStorage).upload(imageCaptor.capture(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE));
    assertThat(imageCaptor.getValue()).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("관리자 뉴스 등록은 지원하지 않는 이미지 형식을 거부한다")
  void createNewsRejectsUnsupportedImage() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest("뉴스 제목", "뉴스 본문", "https://example.com/news/1", Category.STOCK);
    MockMultipartFile image =
        new MockMultipartFile("image", "news.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1, 2, 3});

    assertThatThrownBy(() -> adminNewsService.createDraft(request, image))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.INVALID_IMAGE_FILE);

    verifyNoInteractions(s3ImageStorage, newsRepository);
  }

  @Test
  @DisplayName("관리자 뉴스 목록을 상태와 함께 페이지로 조회한다")
  void getNewsPageReturnsNewsWithStatus() {
    News news =
        News.createAdminDraft("초안 뉴스", "뉴스 본문", null, "https://example.com/news/1", Category.STOCK);
    PageRequest pageable = PageRequest.of(0, 15);
    given(newsRepository.findAdminNews(null, null, pageable))
        .willReturn(new PageImpl<>(List.of(news), pageable, 1));

    var response = adminNewsService.getNewsPage(1, 15, null, null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().title()).isEqualTo("초안 뉴스");
    assertThat(response.items().getFirst().status()).isEqualTo(NewsStatus.DRAFT);
    verify(newsRepository).findAdminNews(null, null, pageable);
  }

  @Test
  @DisplayName("관리자 뉴스 상세는 발행되지 않은 뉴스도 조회한다")
  void getNewsDetailReturnsDraftNews() {
    News news =
        News.createAdminDraft("초안 뉴스", "뉴스 본문", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    News foundNews = adminNewsService.getNewsDetail(1L);

    assertThat(foundNews).isSameAs(news);
    assertThat(foundNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
  }

  @Test
  @DisplayName("관리자 뉴스 발행은 초안 뉴스를 발행 상태로 변경한다")
  void publishNewsChangesDraftStatusToPublished() {
    News news =
        News.createAdminDraft(
            "초안 뉴스", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리 인하", "포인트 1", "포인트 2", "포인트 3"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("시장 분석 결과");
    given(keywordAiClient.extractKeywords("뉴스 본문")).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(marketAnalysis);

    News publishedNews = adminNewsService.publish(1L);

    assertThat(publishedNews).isSameAs(news);
    assertThat(publishedNews.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    assertThat(publishedNews.getPublishedAt()).isNotNull();
    verify(keywordAiClient).extractKeywords("뉴스 본문");
    verify(marketAnalysisAiClient).analyze("뉴스 본문");
    verify(keywordService).saveKeywords(news, keywords);
    verify(marketAnalysisService).saveMarketAnalysis(news, marketAnalysis);
  }

  @Test
  @DisplayName("존재하지 않는 뉴스 발행 요청은 예외를 던진다")
  void publishNewsThrowsWhenNewsIsMissing() {
    given(newsRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminNewsService.publish(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }

  @Test
  @DisplayName("이미 발행된 뉴스는 다시 발행할 수 없다")
  void publishNewsRejectsNonDraftNews() {
    News news =
        News.createPublished(
            "발행 뉴스",
            "뉴스 본문",
            "뉴스 요약",
            null,
            null,
            null,
            "https://example.com/news/1",
            Category.STOCK,
            java.time.Instant.now());
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    assertThatThrownBy(() -> adminNewsService.publish(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_DRAFT);

    verifyNoInteractions(
        keywordAiClient, marketAnalysisAiClient, keywordService, marketAnalysisService);
  }

  @Test
  @DisplayName("관리자 뉴스 거절은 초안 뉴스를 거절 상태로 변경한다")
  void rejectNewsChangesDraftStatusToRejected() {
    News news =
        News.createAdminDraft(
            "초안 뉴스", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    News rejectedNews = adminNewsService.reject(1L);

    assertThat(rejectedNews).isSameAs(news);
    assertThat(rejectedNews.getStatus()).isEqualTo(NewsStatus.REJECTED);
    assertThat(rejectedNews.getPublishedAt()).isNull();
    verifyNoInteractions(
        keywordAiClient, marketAnalysisAiClient, keywordService, marketAnalysisService);
  }

  @Test
  @DisplayName("이미 발행된 뉴스는 거절할 수 없다")
  void rejectNewsRejectsNonDraftNews() {
    News news =
        News.createPublished(
            "발행 뉴스",
            "뉴스 본문",
            "뉴스 요약",
            null,
            null,
            null,
            "https://example.com/news/1",
            Category.STOCK,
            java.time.Instant.now());
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    assertThatThrownBy(() -> adminNewsService.reject(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_DRAFT);
  }

  @Test
  @DisplayName("키워드 추출에 실패하면 뉴스는 초안 상태로 유지한다")
  void publishNewsKeepsDraftWhenKeywordExtractionFails() {
    News news =
        News.createAdminDraft(
            "초안 뉴스", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    RuntimeException failure = new RuntimeException("keyword extraction failed");
    given(keywordAiClient.extractKeywords("뉴스 본문")).willThrow(failure);

    assertThatThrownBy(() -> adminNewsService.publish(1L)).isSameAs(failure);

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DRAFT);
    verifyNoInteractions(marketAnalysisAiClient, keywordService, marketAnalysisService);
  }

  @Test
  @DisplayName("관리자 뉴스 내용을 수정하고 기존 상태는 유지한다")
  void updateNewsChangesContentWithoutChangingStatus() {
    News news =
        News.createAdminDraft(
            "기존 제목",
            "기존 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png",
            "https://example.com/old",
            Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest("수정 제목", "수정 본문", "https://example.com/new", Category.ETF);
    MockMultipartFile image =
        new MockMultipartFile("image", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[] {4, 5, 6});
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(s3ImageStorage.upload(any(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE)))
        .willReturn(
            new S3ImageStorage.StoredImage("new-image.png", "https://example.com/new-image.png"));

    TransactionSynchronizationManager.initSynchronization();
    try {
      News updatedNews = adminNewsService.update(1L, request, image);

      assertThat(updatedNews.getTitle()).isEqualTo("수정 제목");
      assertThat(updatedNews.getContent()).isEqualTo("수정 본문");
      assertThat(updatedNews.getImageUrl()).isEqualTo("https://example.com/new-image.png");
      assertThat(updatedNews.getSourceUrl()).isEqualTo("https://example.com/new");
      assertThat(updatedNews.getCategory()).isEqualTo(Category.ETF);
      assertThat(updatedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
      verify(s3ImageStorage, never())
          .deleteIfManaged("https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png");

      for (TransactionSynchronization synchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCommit();
      }

      verify(s3ImageStorage)
          .deleteIfManaged("https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("뉴스 DB 반영에 실패하면 새 이미지를 정리한다")
  void updateNewsDeletesUploadedImageWhenDatabaseUpdateFails() {
    News news =
        News.createAdminDraft(
            "기존 제목",
            "기존 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png",
            "https://example.com/old",
            Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest("수정 제목", "수정 본문", "https://example.com/new", Category.ETF);
    MockMultipartFile image =
        new MockMultipartFile("image", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[] {4, 5, 6});
    S3ImageStorage.StoredImage uploadedImage =
        new S3ImageStorage.StoredImage("new-image.png", "https://example.com/new-image.png");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(s3ImageStorage.upload(any(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE)))
        .willReturn(uploadedImage);
    org.mockito.BDDMockito.willThrow(new RuntimeException("database update failed"))
        .given(newsRepository)
        .flush();

    assertThatThrownBy(() -> adminNewsService.update(1L, request, image))
        .isInstanceOf(RuntimeException.class);

    verify(s3ImageStorage).delete(uploadedImage);
    verify(s3ImageStorage, never()).deleteIfManaged(news.getImageUrl());
  }

  @Test
  @DisplayName("존재하지 않는 뉴스 수정 요청은 예외를 던진다")
  void updateNewsThrowsWhenNewsIsMissing() {
    given(newsRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                adminNewsService.update(
                    1L,
                    new AdminNewsUpdateRequest(
                        "수정 제목", "수정 본문", "https://example.com/new", Category.ETF),
                    null))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }

  @Test
  @DisplayName("관리자 뉴스 삭제는 삭제 상태로 변경한다")
  void deleteNewsChangesStatusToDeleted() {
    News news =
        News.createAdminDraft(
            "뉴스 제목",
            "뉴스 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/news.png",
            "https://example.com/news/1",
            Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    adminNewsService.delete(1L);

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DELETED);
    assertThat(news.getPublishedAt()).isNull();
    verify(s3ImageStorage).deleteIfManaged(news.getImageUrl());
  }
}
