package org.grit.daynomy.news.service;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsListItemResponse;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AdminNewsService {

  private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

  private final NewsRepository newsRepository;
  private final S3ImageStorage s3ImageStorage;

  @Transactional
  public News createDraft(AdminNewsCreateRequest request, MultipartFile image) {
    UploadedImage uploadedImage = uploadImage(image);
    try {
      News news =
          News.createAdminDraft(
              request.title(),
              request.content(),
              request.description(),
              uploadedImage == null ? null : uploadedImage.url(),
              request.sourceUrl(),
              request.category());

      return newsRepository.save(news);
    } catch (RuntimeException exception) {
      deleteUploadedImage(uploadedImage);
      throw exception;
    }
  }

  public AdminNewsPageResponse getNewsPage(
      int page, int size, NewsStatus status, Category category) {
    Page<News> newsPage =
        newsRepository.findAdminNews(status, category, PageRequest.of(page - 1, size));

    return AdminNewsPageResponse.from(newsPage.map(AdminNewsListItemResponse::from));
  }

  public News getNewsDetail(Long id) {
    return newsRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
  }

  @Transactional
  public News update(Long id, AdminNewsUpdateRequest request) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    news.update(
        request.title(),
        request.content(),
        request.description(),
        request.imageUrl(),
        request.sourceUrl(),
        request.category());
    return news;
  }

  @Transactional
  public void delete(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    news.delete();
  }

  private UploadedImage uploadImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      return null;
    }

    if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
      throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    }

    String contentType = image.getContentType();
    String extension = extensionOf(contentType);
    String fileName = "%s.%s".formatted(UUID.randomUUID(), extension);
    try {
      byte[] content = image.getBytes();
      s3ImageStorage.put(fileName, content, contentType);
      try {
        return new UploadedImage(fileName, s3ImageStorage.publicUrl(fileName));
      } catch (RuntimeException exception) {
        deleteUploadedImage(new UploadedImage(fileName, null));
        throw exception;
      }
    } catch (IOException exception) {
      throw new BusinessException(NewsErrorCode.INVALID_IMAGE_FILE);
    }
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

  private void deleteUploadedImage(UploadedImage uploadedImage) {
    if (uploadedImage == null) {
      return;
    }

    try {
      s3ImageStorage.delete(uploadedImage.relativeKey());
    } catch (BusinessException exception) {
      log.warn("Failed to clean up uploaded news image: key={}", uploadedImage.relativeKey());
    }
  }

  private record UploadedImage(String relativeKey, String url) {}
}
