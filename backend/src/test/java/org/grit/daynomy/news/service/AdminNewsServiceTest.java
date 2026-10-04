package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.OpenAiImageGenerator;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.external.wikimedia.WikimediaImageCandidate;
import org.grit.daynomy.external.wikimedia.WikimediaImageClient;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.ImageSourceInfo;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.dto.ImageSourceRequest;
import org.grit.daynomy.news.dto.NewsSourceRequest;
import org.grit.daynomy.news.dto.WikimediaImageSelectionRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminNewsServiceTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(AdminNewsService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @Mock private NewsRepository newsRepository;

  @Mock private NewsSearchRepository newsSearchRepository;

  @Mock private OpenAiImageGenerator openAiImageGenerator;

  @Mock private S3ImageStorage s3ImageStorage;

  @Mock private WikimediaImageClient wikimediaImageClient;

  @Mock private KeywordAiClient keywordAiClient;

  @Mock private MarketAnalysisAiClient marketAnalysisAiClient;

  @Mock private KeywordService keywordService;

  @Mock private MarketAnalysisService marketAnalysisService;

  @InjectMocks private AdminNewsService adminNewsService;

  @BeforeEach
  void setUpLogging() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  @DisplayName("관리자 뉴스 등록은 수동 출처의 초안으로 저장한다")
  void createNewsSavesManualDraft() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목",
            "뉴스 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/news/1")),
            Category.STOCK,
            new ImageSourceRequest("Unsplash", "https://unsplash.com/photos/example"));
    MockMultipartFile image =
        new MockMultipartFile("image", "news.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});
    given(s3ImageStorage.upload(any(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE)))
        .willReturn(
            new S3ImageStorage.StoredImage("news-image.png", "https://example.com/news-image.png"));
    given(newsRepository.save(any(News.class))).willAnswer(invocation -> invocation.getArgument(0));

    TransactionSynchronizationManager.initSynchronization();
    News savedNews = adminNewsService.createDraft(request, image);

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    News capturedNews = newsCaptor.getValue();
    assertThat(savedNews).isSameAs(capturedNews);
    assertThat(capturedNews.getTitle()).isEqualTo("뉴스 제목");
    assertThat(capturedNews.getContent()).isEqualTo("뉴스 본문");
    assertThat(capturedNews.getImageUrl()).isEqualTo("https://example.com/news-image.png");
    assertThat(capturedNews.getImageSource())
        .isEqualTo(
            new ImageSourceInfo("Unsplash", "https://unsplash.com/photos/example", "", "", ""));
    assertThat(capturedNews.getSources())
        .containsExactly(new NewsSourceInfo("직접 입력", "https://example.com/news/1"));
    assertThat(capturedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
    assertThat(capturedNews.getPublishedAt()).isNull();
    ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(s3ImageStorage).upload(imageCaptor.capture(), eq("png"), eq(MediaType.IMAGE_PNG_VALUE));
    assertThat(imageCaptor.getValue()).containsExactly(1, 2, 3);
    assertThat(appender.list).isEmpty();

    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_DRAFT_CREATED);
    assertThat(keyValues(log)).containsKey("newsId").containsEntry("category", Category.STOCK);
  }

  @Test
  @DisplayName("Wikimedia Commons 선택 이미지는 S3와 라이선스 출처를 함께 저장한다")
  void createNewsSavesSelectedWikimediaImage() {
    WikimediaImageCandidate candidate =
        new WikimediaImageCandidate(
            "File:Seoul skyline.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/seoul.jpg",
            "https://commons.wikimedia.org/wiki/File:Seoul_skyline.jpg",
            "Jane Doe",
            "CC BY 4.0",
            "https://creativecommons.org/licenses/by/4.0/",
            1200,
            800);
    byte[] image = {1, 2, 3};
    given(wikimediaImageClient.download(candidate.title()))
        .willReturn(new WikimediaImageClient.ImportedImage(candidate, image, "image/jpeg", "jpg"));
    given(s3ImageStorage.upload(image, "jpg", "image/jpeg"))
        .willReturn(
            new S3ImageStorage.StoredImage("wikimedia.jpg", "https://example.com/wikimedia.jpg"));
    given(newsRepository.save(any(News.class))).willAnswer(invocation -> invocation.getArgument(0));
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목",
            "뉴스 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/news/1")),
            Category.STOCK,
            null,
            new WikimediaImageSelectionRequest(candidate.title()));

    TransactionSynchronizationManager.initSynchronization();
    try {
      News savedNews = adminNewsService.createDraft(request, null);

      assertThat(savedNews.getImageUrl()).isEqualTo("https://example.com/wikimedia.jpg");
      assertThat(savedNews.getImageSource())
          .isEqualTo(
              ImageSourceInfo.wikimedia(
                  "Wikimedia Commons",
                  candidate.sourceUrl(),
                  "Jane Doe",
                  "CC BY 4.0",
                  "https://creativecommons.org/licenses/by/4.0/"));
      verify(wikimediaImageClient).download(candidate.title());
      verify(s3ImageStorage).upload(image, "jpg", "image/jpeg");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("관리자 뉴스 등록은 여러 출처를 JSONB 목록으로 저장한다")
  void createNewsSavesMultipleSources() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목",
            "뉴스 본문",
            List.of(
                new NewsSourceRequest("출처 A", "https://example.com/a"),
                new NewsSourceRequest("출처 B", "https://example.com/b")),
            Category.STOCK);
    given(newsRepository.save(any(News.class))).willAnswer(invocation -> invocation.getArgument(0));

    TransactionSynchronizationManager.initSynchronization();
    adminNewsService.createDraft(request, null);

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    assertThat(newsCaptor.getValue().getSources())
        .containsExactly(
            new NewsSourceInfo("출처 A", "https://example.com/a"),
            new NewsSourceInfo("출처 B", "https://example.com/b"));
    assertThat(newsCaptor.getValue().getImageSource()).isEqualTo(ImageSourceInfo.empty());
    commitTransaction();
  }

  @Test
  @DisplayName("초안 이미지 생성은 S3 URL을 뉴스 초안에 저장한다")
  void generateImageStoresImageForDraft() {
    News news = News.createDraft("뉴스 제목", "뉴스 본문", null, List.of(), Category.STOCK);
    byte[] image = {1, 2, 3};
    S3ImageStorage.StoredImage uploadedImage =
        new S3ImageStorage.StoredImage("generated.webp", "https://example.com/generated.webp");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(openAiImageGenerator.generateEconomicNewsImage("뉴스 제목", "뉴스 본문", Category.STOCK))
        .willReturn(image);
    given(s3ImageStorage.upload(image, "webp", "image/webp")).willReturn(uploadedImage);

    TransactionSynchronizationManager.initSynchronization();
    try {
      News updatedNews = adminNewsService.generateImage(1L);

      assertThat(updatedNews.getImageUrl()).isEqualTo(uploadedImage.publicUrl());
      verify(newsRepository).flush();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
      verify(s3ImageStorage).delete(uploadedImage);
      assertThat(appender.list).isEmpty();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("초안 이미지 저장에 실패하면 S3에 업로드한 이미지를 정리하고 예외를 전달한다")
  void generateImageCleansUpWhenNewsUpdateFails() {
    News news = News.createDraft("뉴스 제목", "뉴스 본문", null, List.of(), Category.STOCK);
    byte[] image = {1, 2, 3};
    S3ImageStorage.StoredImage uploadedImage =
        new S3ImageStorage.StoredImage("generated.webp", "https://example.com/generated.webp");
    RuntimeException failure = new RuntimeException("database update failed");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(openAiImageGenerator.generateEconomicNewsImage("뉴스 제목", "뉴스 본문", Category.STOCK))
        .willReturn(image);
    given(s3ImageStorage.upload(image, "webp", "image/webp")).willReturn(uploadedImage);
    willThrow(failure).given(newsRepository).flush();

    assertThatThrownBy(() -> adminNewsService.generateImage(1L)).isSameAs(failure);

    verify(s3ImageStorage).delete(uploadedImage);
  }

  @Test
  @DisplayName("발행된 뉴스 이미지를 재생성하고 기존 S3 이미지는 커밋 후 정리한다")
  void generateImageReplacesPublishedImage() {
    String previousImageUrl = "https://example.com/existing.webp";
    News news =
        News.createPublished(
            "뉴스 제목",
            "뉴스 본문",
            previousImageUrl,
            new ImageSourceInfo("Unsplash", "https://unsplash.com/photos/example", "", "", ""),
            List.of(),
            Category.STOCK,
            null);
    byte[] image = {1, 2, 3};
    S3ImageStorage.StoredImage uploadedImage =
        new S3ImageStorage.StoredImage("generated.webp", "https://example.com/generated.webp");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(openAiImageGenerator.generateEconomicNewsImage("뉴스 제목", "뉴스 본문", Category.STOCK))
        .willReturn(image);
    given(s3ImageStorage.upload(image, "webp", "image/webp")).willReturn(uploadedImage);

    TransactionSynchronizationManager.initSynchronization();
    try {
      News result = adminNewsService.generateImage(1L);

      assertThat(result.getImageUrl()).isEqualTo(uploadedImage.publicUrl());
      assertThat(result.getImageSource()).isEqualTo(ImageSourceInfo.aiGenerated());
      verify(newsRepository).flush();
      assertThat(appender.list).isEmpty();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization -> {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
              });
      verify(s3ImageStorage).deleteIfManaged(previousImageUrl);
      ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_IMAGE_GENERATED);
      assertThat(keyValues(log)).containsKey("newsId");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("삭제된 뉴스는 이미지 생성을 거부한다")
  void generateImageRejectsDeletedNews() {
    News news = News.createDraft("뉴스 제목", "뉴스 본문", null, List.of(), Category.STOCK);
    news.delete();
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    assertThatThrownBy(() -> adminNewsService.generateImage(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(NewsErrorCode.NEWS_IMAGE_GENERATION_NOT_ALLOWED));

    verifyNoInteractions(openAiImageGenerator, s3ImageStorage);
  }

  @Test
  @DisplayName("관리자 뉴스 등록은 지원하지 않는 이미지 형식을 거부한다")
  void createNewsRejectsUnsupportedImage() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목",
            "뉴스 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    MockMultipartFile image =
        new MockMultipartFile("image", "news.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1, 2, 3});

    assertThatThrownBy(() -> adminNewsService.createDraft(request, image))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.INVALID_IMAGE_FILE);

    verifyNoInteractions(s3ImageStorage, newsRepository);
  }

  @Test
  @DisplayName("관리자 뉴스 목록은 필터가 없으면 전체를 페이지로 조회한다")
  void getNewsPageReturnsAllNewsWithoutFilters() {
    News news =
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    PageRequest pageable = PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    given(newsRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(news), pageable, 1));

    var response = adminNewsService.getNewsPage(1, 15, null, null, "  ");

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().title()).isEqualTo("초안 뉴스");
    assertThat(response.items().getFirst().status()).isEqualTo(NewsStatus.DRAFT);
    verify(newsRepository).findAll(pageable);
    verifyNoInteractions(newsSearchRepository);
  }

  @Test
  @DisplayName("관리자 뉴스 검색은 검색어를 이스케이프하고 필터·정렬·페이지를 전달한다")
  void getNewsPageSearchesWithFilters() {
    News news = News.createDraft("금%리_ 뉴스", "본문", null, List.of(), Category.STOCK);
    PageRequest pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    given(newsSearchRepository.search("금!%리!_", Category.STOCK, NewsStatus.DRAFT, pageable))
        .willReturn(new PageImpl<>(List.of(news), pageable, 3));

    var response = adminNewsService.getNewsPage(2, 2, NewsStatus.DRAFT, Category.STOCK, " 금%리_ ");

    assertThat(response.items()).extracting("title").containsExactly("금%리_ 뉴스");
    assertThat(response.totalElements()).isEqualTo(3);
    verify(newsSearchRepository).search("금!%리!_", Category.STOCK, NewsStatus.DRAFT, pageable);
    verifyNoInteractions(newsRepository);
  }

  @Test
  @DisplayName("관리자 뉴스 상세는 발행되지 않은 뉴스도 조회한다")
  void getNewsDetailReturnsDraftNews() {
    News news =
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    News foundNews = adminNewsService.getNewsDetail(1L);

    assertThat(foundNews).isSameAs(news);
    assertThat(foundNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
  }

  @Test
  @DisplayName("관리자 뉴스 발행은 초안 뉴스를 발행 상태로 변경한다")
  void publishNewsChangesDraftStatusToPublished() {
    News news =
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리 인하", "포인트 1", "포인트 2", "포인트 3"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("시장 분석 결과");
    given(keywordAiClient.extractKeywords("뉴스 본문")).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(marketAnalysis);

    TransactionSynchronizationManager.initSynchronization();
    News publishedNews = adminNewsService.publish(1L);

    assertThat(publishedNews).isSameAs(news);
    assertThat(publishedNews.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    assertThat(publishedNews.getPublishedAt()).isNotNull();
    verify(keywordAiClient).extractKeywords("뉴스 본문");
    verify(marketAnalysisAiClient).analyze("뉴스 본문");
    verify(keywordService).saveKeywords(news, keywords);
    verify(marketAnalysisService).saveMarketAnalysis(news, marketAnalysis);
    verify(newsRepository).flush();
    assertThat(appender.list).isEmpty();

    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_PUBLISH_COMPLETED);
    assertThat(keyValues(log)).containsKey("newsId");
  }

  @Test
  @DisplayName("이미 발행 처리된 뉴스의 시장 분석 중복 저장은 409 예외로 변환한다")
  void publishNewsConvertsMarketAnalysisUniqueViolation() {
    News news =
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리 인하", "포인트 1", "포인트 2", "포인트 3"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("시장 분석 결과");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(keywordAiClient.extractKeywords("뉴스 본문")).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("뉴스 본문")).willReturn(marketAnalysis);
    org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("duplicate news_id"))
        .given(newsRepository)
        .flush();

    assertThatThrownBy(() -> adminNewsService.publish(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_DRAFT);

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DRAFT);
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
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
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
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    TransactionSynchronizationManager.initSynchronization();
    News rejectedNews = adminNewsService.reject(1L);

    assertThat(rejectedNews).isSameAs(news);
    assertThat(rejectedNews.getStatus()).isEqualTo(NewsStatus.REJECTED);
    assertThat(rejectedNews.getPublishedAt()).isNull();
    verifyNoInteractions(
        keywordAiClient, marketAnalysisAiClient, keywordService, marketAnalysisService);
    assertThat(appender.list).isEmpty();

    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_REJECT_COMPLETED);
    assertThat(keyValues(log)).containsKey("newsId");
  }

  @Test
  @DisplayName("이미 발행된 뉴스는 거절할 수 없다")
  void rejectNewsRejectsNonDraftNews() {
    News news =
        News.createPublished(
            "발행 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
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
        News.createDraft(
            "초안 뉴스",
            "뉴스 본문",
            null,
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
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
        News.createDraft(
            "기존 제목",
            "기존 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png",
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/old")),
            Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "수정 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
            Category.ETF,
            new ImageSourceRequest("Pexels", "https://pexels.com/photo/example"));
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
      assertThat(updatedNews.getImageSource())
          .isEqualTo(new ImageSourceInfo("Pexels", "https://pexels.com/photo/example", "", "", ""));
      assertThat(updatedNews.getSources())
          .containsExactly(new NewsSourceInfo("직접 입력", "https://example.com/new"));
      assertThat(updatedNews.getCategory()).isEqualTo(Category.ETF);
      assertThat(updatedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
      verifyNoInteractions(
          keywordAiClient, marketAnalysisAiClient, keywordService, marketAnalysisService);
      verify(s3ImageStorage, never())
          .deleteIfManaged("https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png");
      assertThat(appender.list).isEmpty();

      for (TransactionSynchronization synchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCommit();
      }

      verify(s3ImageStorage)
          .deleteIfManaged("https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png");
      ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_UPDATE_COMPLETED);
      assertThat(keyValues(log)).containsKey("newsId").containsEntry("analysisRegenerated", false);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("발행된 뉴스의 본문을 수정하면 키워드와 시장 분석을 다시 생성한다")
  void updatePublishedNewsRegeneratesKeywordsAndMarketAnalysis() {
    News news =
        News.createPublished(
            "기존 제목",
            "기존 본문",
            null,
            List.of(new NewsSourceInfo("DART", "https://example.com/old")),
            Category.STOCK,
            java.time.Instant.now());
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "수정 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
            Category.ETF);
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리 인하", "포인트 1", "포인트 2", "포인트 3"));
    NewsMarketAnalysis marketAnalysis = new NewsMarketAnalysis("수정된 시장 분석 결과");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(keywordAiClient.extractKeywords("수정 본문")).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("수정 본문")).willReturn(marketAnalysis);

    TransactionSynchronizationManager.initSynchronization();
    News updatedNews = adminNewsService.update(1L, request, null);

    assertThat(updatedNews.getContent()).isEqualTo("수정 본문");
    verify(keywordAiClient).extractKeywords("수정 본문");
    verify(marketAnalysisAiClient).analyze("수정 본문");
    verify(keywordService).replaceKeywords(news, keywords);
    verify(marketAnalysisService).updateMarketAnalysis(1L, marketAnalysis);
    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_UPDATE_COMPLETED);
    assertThat(keyValues(log)).containsKey("newsId").containsEntry("analysisRegenerated", true);
  }

  @Test
  @DisplayName("기존 이미지 출처 메타데이터를 수정해도 출처 유형은 유지한다")
  void updateImageSourceMetadataKeepsExistingType() {
    String sourceUrl = "https://commons.wikimedia.org/wiki/File:Seoul_skyline.jpg";
    News news =
        News.createDraft(
            "기존 제목",
            "기존 본문",
            "https://example.com/wikimedia.jpg",
            ImageSourceInfo.wikimedia(
                "Wikimedia Commons",
                sourceUrl,
                "기존 저작자",
                "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/"),
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "수정 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/news/1")),
            Category.STOCK,
            new ImageSourceRequest(
                "Wikimedia Commons",
                sourceUrl,
                "수정 저작자",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/"));
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    TransactionSynchronizationManager.initSynchronization();
    try {
      News updatedNews = adminNewsService.update(1L, request, null);

      assertThat(updatedNews.getImageSource())
          .isEqualTo(
              ImageSourceInfo.wikimedia(
                  "Wikimedia Commons",
                  sourceUrl,
                  "수정 저작자",
                  "CC BY-SA 4.0",
                  "https://creativecommons.org/licenses/by-sa/4.0/"));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("시장 분석 생성에 실패하면 뉴스와 기존 분석 데이터를 변경하지 않는다")
  void updatePublishedNewsKeepsExistingDataWhenMarketAnalysisGenerationFails() {
    News news =
        News.createPublished(
            "기존 제목",
            "기존 본문",
            null,
            List.of(new NewsSourceInfo("DART", "https://example.com/old")),
            Category.STOCK,
            java.time.Instant.now());
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "수정 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
            Category.ETF);
    List<NewsKeyword> keywords =
        List.of(new NewsKeyword(KeywordCategory.POLICY, "금리 인하", "포인트 1", "포인트 2", "포인트 3"));
    RuntimeException failure = new RuntimeException("market analysis generation failed");
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));
    given(keywordAiClient.extractKeywords("수정 본문")).willReturn(keywords);
    given(marketAnalysisAiClient.analyze("수정 본문")).willThrow(failure);

    assertThatThrownBy(() -> adminNewsService.update(1L, request, null)).isSameAs(failure);

    assertThat(news.getTitle()).isEqualTo("기존 제목");
    assertThat(news.getContent()).isEqualTo("기존 본문");
    verifyNoInteractions(keywordService, marketAnalysisService);
  }

  @Test
  @DisplayName("발행된 뉴스의 본문이 변경되지 않으면 키워드와 시장 분석을 다시 생성하지 않는다")
  void updatePublishedNewsWithoutContentChangeDoesNotRegenerateAnalysis() {
    News news =
        News.createPublished(
            "기존 제목",
            "기존 본문",
            null,
            List.of(new NewsSourceInfo("DART", "https://example.com/old")),
            Category.STOCK,
            java.time.Instant.now());
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "기존 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
            Category.ETF);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    TransactionSynchronizationManager.initSynchronization();
    News updatedNews = adminNewsService.update(1L, request, null);

    assertThat(updatedNews.getTitle()).isEqualTo("수정 제목");
    assertThat(updatedNews.getCategory()).isEqualTo(Category.ETF);
    verifyNoInteractions(
        keywordAiClient, marketAnalysisAiClient, keywordService, marketAnalysisService);
    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_UPDATE_COMPLETED);
    assertThat(keyValues(log)).containsKey("newsId").containsEntry("analysisRegenerated", false);
  }

  @Test
  @DisplayName("뉴스 DB 반영에 실패하면 새 이미지를 정리한다")
  void updateNewsDeletesUploadedImageWhenDatabaseUpdateFails() {
    News news =
        News.createDraft(
            "기존 제목",
            "기존 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/old.png",
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/old")),
            Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목",
            "수정 본문",
            List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
            Category.ETF);
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
                        "수정 제목",
                        "수정 본문",
                        List.of(new NewsSourceRequest("직접 입력", "https://example.com/new")),
                        Category.ETF),
                    null))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }

  @Test
  @DisplayName("관리자 뉴스 삭제는 삭제 상태로 변경한다")
  void deleteNewsChangesStatusToDeleted() {
    News news =
        News.createDraft(
            "뉴스 제목",
            "뉴스 본문",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/news.png",
            List.of(new NewsSourceInfo("직접 입력", "https://example.com/news/1")),
            Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    TransactionSynchronizationManager.initSynchronization();
    adminNewsService.delete(1L);

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DELETED);
    assertThat(news.getPublishedAt()).isNull();
    verify(s3ImageStorage).deleteIfManaged(news.getImageUrl());
    assertThat(appender.list).isEmpty();

    commitTransaction();
    ILoggingEvent log = assertCompletionLog(LogEvent.NEWS_DELETE_COMPLETED);
    assertThat(keyValues(log)).containsKey("newsId");
  }

  private void commitTransaction() {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    TransactionSynchronizationManager.clearSynchronization();
  }

  private ILoggingEvent assertCompletionLog(LogEvent event) {
    ILoggingEvent loggingEvent =
        appender.list.stream()
            .filter(candidate -> event.code().equals(keyValues(candidate).get("event")))
            .findFirst()
            .orElseThrow();
    assertThat(loggingEvent.getLevel()).isEqualTo(Level.INFO);
    assertThat(loggingEvent.getFormattedMessage()).isEqualTo(event.message());
    return loggingEvent;
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
