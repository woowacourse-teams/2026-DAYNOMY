package org.grit.daynomy.news.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.OpenAiImageGenerator;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.external.wikimedia.WikimediaImageCandidate;
import org.grit.daynomy.external.wikimedia.WikimediaImageClient;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.ImageSourceInfo;
import org.grit.daynomy.news.domain.ImageSourceType;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsListItemResponse;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.dto.WikimediaImageCandidateResponse;
import org.grit.daynomy.news.dto.WikimediaImageSelectionRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AdminNewsService {

  private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

  private final NewsRepository newsRepository;
  private final NewsSearchRepository newsSearchRepository;
  private final OpenAiImageGenerator openAiImageGenerator;
  private final S3ImageStorage s3ImageStorage;
  private final WikimediaImageClient wikimediaImageClient;
  private final KeywordAiClient keywordAiClient;
  private final MarketAnalysisAiClient marketAnalysisAiClient;
  private final KeywordService keywordService;
  private final MarketAnalysisService marketAnalysisService;

  @Transactional
  public News createDraft(AdminNewsCreateRequest request, MultipartFile image) {
    ImageUpload imageUpload = uploadImage(image, request.imageSelection());
    try {
      News news =
          News.createDraft(
              request.title(),
              request.content(),
              imageUpload.storedImage() == null ? null : imageUpload.storedImage().publicUrl(),
              imageSourceForCreate(request, imageUpload),
              request.sourceInfos(),
              request.category());

      News savedNews = newsRepository.save(news);
      registerAfterCommit(
          () ->
              log.atInfo()
                  .addKeyValue("event", LogEvent.NEWS_DRAFT_CREATED.code())
                  .addKeyValue("newsId", savedNews.getId())
                  .addKeyValue("category", savedNews.getCategory())
                  .log(LogEvent.NEWS_DRAFT_CREATED.message()));
      return savedNews;
    } catch (RuntimeException exception) {
      deleteUploadedImage(imageUpload.storedImage());
      throw exception;
    }
  }

  public List<WikimediaImageCandidateResponse> searchWikimediaImages(String keyword) {
    return wikimediaImageClient.search(keyword).stream()
        .map(WikimediaImageCandidateResponse::from)
        .toList();
  }

  public AdminNewsPageResponse getNewsPage(
      int page, int size, NewsStatus status, Category category, String keyword) {
    PageRequest pageable =
        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    Page<News> newsPage;
    if (keyword != null && !keyword.isBlank()) {
      String escapedKeyword =
          keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
      newsPage = newsSearchRepository.search(escapedKeyword, category, status, pageable);
    } else if (status == null && category == null) {
      newsPage = newsRepository.findAll(pageable);
    } else if (status == null) {
      newsPage = newsRepository.findByCategory(category, pageable);
    } else if (category == null) {
      newsPage = newsRepository.findByStatus(status, pageable);
    } else {
      newsPage = newsRepository.findByStatusAndCategory(status, category, pageable);
    }

    return AdminNewsPageResponse.from(newsPage.map(AdminNewsListItemResponse::from));
  }

  public News getNewsDetail(Long id) {
    return newsRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
  }

  @Transactional
  public News generateImage(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    if (!news.isDraft() && !news.isPublished()) {
      throw new BusinessException(NewsErrorCode.NEWS_IMAGE_GENERATION_NOT_ALLOWED);
    }

    String previousImageUrl = news.getImageUrl();
    byte[] image =
        openAiImageGenerator.generateEconomicNewsImage(
            news.getTitle(), news.getContent(), news.getCategory());
    S3ImageStorage.StoredImage uploadedImage = s3ImageStorage.upload(image, "webp", "image/webp");
    try {
      news.updateImage(uploadedImage.publicUrl(), ImageSourceInfo.aiGenerated());
      newsRepository.flush();
      registerImageCleanup(previousImageUrl, uploadedImage);
      registerAfterCommit(
          () ->
              log.atInfo()
                  .addKeyValue("event", LogEvent.NEWS_IMAGE_GENERATED.code())
                  .addKeyValue("newsId", news.getId())
                  .log(LogEvent.NEWS_IMAGE_GENERATED.message()));
      return news;
    } catch (RuntimeException exception) {
      deleteUploadedImage(uploadedImage);
      throw exception;
    }
  }

  @Transactional
  public News publish(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    if (!news.isDraft()) {
      throw new BusinessException(NewsErrorCode.NEWS_NOT_DRAFT);
    }

    List<NewsKeyword> keywords = keywordAiClient.extractKeywords(news.getContent());
    NewsMarketAnalysis marketAnalysis = marketAnalysisAiClient.analyze(news.getContent());
    keywordService.saveKeywords(news, keywords);
    try {
      marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);
      newsRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(NewsErrorCode.NEWS_NOT_DRAFT);
    }
    news.publish();
    registerAfterCommit(
        () ->
            log.atInfo()
                .addKeyValue("event", LogEvent.NEWS_PUBLISH_COMPLETED.code())
                .addKeyValue("newsId", news.getId())
                .log(LogEvent.NEWS_PUBLISH_COMPLETED.message()));
    return news;
  }

  @Transactional
  public News reject(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    news.reject();
    registerAfterCommit(
        () ->
            log.atInfo()
                .addKeyValue("event", LogEvent.NEWS_REJECT_COMPLETED.code())
                .addKeyValue("newsId", news.getId())
                .log(LogEvent.NEWS_REJECT_COMPLETED.message()));
    return news;
  }

  @Transactional
  public News update(Long id, AdminNewsUpdateRequest request, MultipartFile image) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    boolean shouldRegenerateAnalysis =
        news.isPublished() && !news.getContent().equals(request.content());
    String previousImageUrl = news.getImageUrl();
    ImageUpload imageUpload = uploadImage(image, request.imageSelection());
    S3ImageStorage.StoredImage uploadedImage = imageUpload.storedImage();
    try {
      if (shouldRegenerateAnalysis) {
        List<NewsKeyword> keywords = keywordAiClient.extractKeywords(request.content());
        NewsMarketAnalysis marketAnalysis = marketAnalysisAiClient.analyze(request.content());
        keywordService.replaceKeywords(news, keywords);
        marketAnalysisService.updateMarketAnalysis(id, marketAnalysis);
      }
      news.update(
          request.title(),
          request.content(),
          uploadedImage == null ? previousImageUrl : uploadedImage.publicUrl(),
          imageSourceForUpdate(news, request, imageUpload),
          request.sourceInfos(),
          request.category());
      if (uploadedImage != null) {
        newsRepository.flush();
        registerImageCleanup(previousImageUrl, uploadedImage);
      }
      registerAfterCommit(
          () ->
              log.atInfo()
                  .addKeyValue("event", LogEvent.NEWS_UPDATE_COMPLETED.code())
                  .addKeyValue("newsId", news.getId())
                  .addKeyValue("analysisRegenerated", shouldRegenerateAnalysis)
                  .log(LogEvent.NEWS_UPDATE_COMPLETED.message()));
      return news;
    } catch (RuntimeException exception) {
      deleteUploadedImage(uploadedImage);
      throw exception;
    }
  }

  @Transactional
  public void delete(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    news.delete();
    s3ImageStorage.deleteIfManaged(news.getImageUrl());
    registerAfterCommit(
        () ->
            log.atInfo()
                .addKeyValue("event", LogEvent.NEWS_DELETE_COMPLETED.code())
                .addKeyValue("newsId", news.getId())
                .log(LogEvent.NEWS_DELETE_COMPLETED.message()));
  }

  private ImageUpload uploadImage(
      MultipartFile image, WikimediaImageSelectionRequest imageSelection) {
    if (image != null && !image.isEmpty() && imageSelection != null) {
      throw new BusinessException(NewsErrorCode.INVALID_WIKIMEDIA_IMAGE);
    }

    if (image == null || image.isEmpty()) {
      if (imageSelection == null) {
        return new ImageUpload(null, null);
      }

      WikimediaImageClient.ImportedImage importedImage =
          wikimediaImageClient.download(imageSelection.title());
      S3ImageStorage.StoredImage storedImage =
          s3ImageStorage.upload(
              importedImage.content(), importedImage.extension(), importedImage.contentType());
      WikimediaImageCandidate candidate = importedImage.candidate();
      return new ImageUpload(
          storedImage,
          ImageSourceInfo.wikimedia(
              "Wikimedia Commons",
              candidate.sourceUrl(),
              candidate.author(),
              candidate.license(),
              candidate.licenseUrl()));
    }

    if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
      throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    }

    String contentType = image.getContentType();
    String extension = extensionOf(contentType);
    try {
      byte[] content = image.getBytes();
      return new ImageUpload(
          s3ImageStorage.upload(content, extension, contentType), ImageSourceInfo.manual());
    } catch (IOException exception) {
      throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    }
  }

  private ImageSourceInfo imageSourceForUpdate(
      News news, AdminNewsUpdateRequest request, ImageUpload imageUpload) {
    if (imageUpload.source() != null) {
      if (imageUpload.source().type() == ImageSourceType.MANUAL
          && request.imageSourceInfo().type() != ImageSourceType.NONE) {
        return request.imageSourceInfo();
      }
      return imageUpload.source();
    }

    ImageSourceInfo requestedSource = request.imageSourceInfo();
    if (requestedSource.type() == ImageSourceType.NONE) {
      return news.getImageSource();
    }

    ImageSourceInfo existingSource = news.getImageSource();
    ImageSourceType type =
        hasSameIdentity(existingSource, requestedSource)
            ? existingSource.type()
            : requestedSource.type();

    return new ImageSourceInfo(
        requestedSource.name(),
        requestedSource.url(),
        requestedSource.author(),
        requestedSource.license(),
        requestedSource.licenseUrl(),
        type);
  }

  private boolean hasSameIdentity(ImageSourceInfo first, ImageSourceInfo second) {
    return first.name().equals(second.name()) && first.url().equals(second.url());
  }

  private ImageSourceInfo imageSourceForCreate(
      AdminNewsCreateRequest request, ImageUpload imageUpload) {
    if (imageUpload.source() == null) {
      return request.imageSourceInfo();
    }
    if (imageUpload.source().type() == ImageSourceType.MANUAL
        && request.imageSourceInfo().type() != ImageSourceType.NONE) {
      return request.imageSourceInfo();
    }
    return imageUpload.source();
  }

  private String extensionOf(String contentType) {
    if (contentType == null) {
      throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    }

    return switch (contentType.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      default -> throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    };
  }

  private void deleteUploadedImage(S3ImageStorage.StoredImage uploadedImage) {
    if (uploadedImage == null) {
      return;
    }

    try {
      s3ImageStorage.delete(uploadedImage);
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.EXTERNAL_DELETE_FAILED.code())
          .addKeyValue("provider", "s3")
          .addKeyValue("relativeKey", uploadedImage.relativeKey())
          .log(LogEvent.EXTERNAL_DELETE_FAILED.message());
    }
  }

  private void registerImageCleanup(
      String previousImageUrl, S3ImageStorage.StoredImage uploadedImage) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deletePreviousImage(previousImageUrl);
          }

          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              deleteUploadedImage(uploadedImage);
            }
          }
        });
  }

  private void registerAfterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void deletePreviousImage(String previousImageUrl) {
    try {
      s3ImageStorage.deleteIfManaged(previousImageUrl);
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.EXTERNAL_DELETE_FAILED.code())
          .addKeyValue("provider", "s3")
          .log(LogEvent.EXTERNAL_DELETE_FAILED.message());
    }
  }

  private record ImageUpload(S3ImageStorage.StoredImage storedImage, ImageSourceInfo source) {}
}
