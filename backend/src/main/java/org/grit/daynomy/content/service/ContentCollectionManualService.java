package org.grit.daynomy.content.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionManualRequest;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ContentCollectionManualService {

  private final ContentCollectionSourceRepository sourceRepository;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;
  private final ContentCollectionPersistenceService persistenceService;

  public ContentCollectionResultResponse register(
      Long sourceId, ContentCollectionManualRequest request) {
    ContentCollectionSource source =
        sourceRepository
            .findById(sourceId)
            .orElseThrow(
                () -> new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));
    if (source.getSourceType() != ContentSourceType.X
        && source.getSourceType() != ContentSourceType.THREADS) {
      throw invalidPost();
    }
    String fallbackTitle = fallbackTitle(source, request.title());
    SocialPostCandidate candidate =
        socialPostSearchClient
            .resolvePost(request.url(), fallbackTitle, request.publishedDate())
            .orElseThrow(ContentCollectionManualService::invalidPost);
    if (!matchesPlatform(source.getSourceType(), candidate)
        || !ContentCollectionSourceUrl.belongsToSource(
            source.getSourceType(), source.getSourceUrl(), candidate.url())) {
      throw invalidPost();
    }
    LocalDate publishedDate =
        candidate.publishedDate().isBlank()
            ? request.publishedDate()
            : LocalDate.parse(candidate.publishedDate());
    return persistenceService.persistManual(
        sourceId,
        new CollectedContentItem(
            candidate.title(),
            normalize(request.description()),
            candidate.url(),
            null,
            publishedDate));
  }

  private boolean matchesPlatform(ContentSourceType sourceType, SocialPostCandidate candidate) {
    return (sourceType == ContentSourceType.X
            && candidate.platform() == SocialPostCandidate.Platform.X)
        || (sourceType == ContentSourceType.THREADS
            && candidate.platform() == SocialPostCandidate.Platform.THREADS);
  }

  private String fallbackTitle(ContentCollectionSource source, String title) {
    String normalized = normalize(title);
    if (!normalized.isBlank()) {
      return normalized;
    }
    return source.getName()
        + (source.getSourceType() == ContentSourceType.X ? " X 게시물" : " Threads 게시물");
  }

  private String normalize(String value) {
    return value == null ? "" : value.strip();
  }

  private static BusinessException invalidPost() {
    return new BusinessException(ContentErrorCode.CONTENT_COLLECTION_POST_INVALID);
  }
}
