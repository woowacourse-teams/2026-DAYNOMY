package org.grit.daynomy.content.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ContentCollectionService {

  private static final ZoneId COLLECTION_ZONE = ZoneId.of("Asia/Seoul");

  private final ContentCollectionSourceRepository sourceRepository;
  private final YouTubeClient youtubeClient;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;
  private final ContentCollectionPersistenceService persistenceService;

  @Value("${content.collection.lookback-days:3}")
  private int configuredLookbackDays;

  public ContentCollectionResultResponse collect(Long sourceId) {
    ContentCollectionSource source = getSource(sourceId);
    Instant now = Instant.now();
    int lookbackDays = Math.min(31, Math.max(1, configuredLookbackDays));
    Instant from = now.minus(java.time.Duration.ofDays(lookbackDays));
    List<CollectedContentItem> items =
        switch (source.getSourceType()) {
          case YOUTUBE -> collectYouTube(source.getSourceUrl(), from, now);
          case X, THREADS -> collectSocial(source);
          default ->
              throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_INVALID);
        };
    return persistenceService.persist(sourceId, items, now);
  }

  private ContentCollectionSource getSource(Long sourceId) {
    return sourceRepository
        .findById(sourceId)
        .orElseThrow(
            () -> new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));
  }

  private List<CollectedContentItem> collectYouTube(String sourceUrl, Instant from, Instant to) {
    return youtubeClient.listChannelUploads(sourceUrl, from, to).stream()
        .map(this::toItem)
        .toList();
  }

  private List<CollectedContentItem> collectSocial(ContentCollectionSource source) {
    String xUrl =
        source.getSourceType() == org.grit.daynomy.content.domain.ContentSourceType.X
            ? source.getSourceUrl()
            : null;
    String threadsUrl =
        source.getSourceType() == org.grit.daynomy.content.domain.ContentSourceType.THREADS
            ? source.getSourceUrl()
            : null;
    return socialPostSearchClient.searchSourcePosts(source.getName(), xUrl, threadsUrl).stream()
        .map(this::toItem)
        .toList();
  }

  private CollectedContentItem toItem(YouTubeVideoCandidate candidate) {
    return new CollectedContentItem(
        candidate.title(),
        candidate.description(),
        candidate.url(),
        candidate.thumbnailUrl(),
        parseDateTime(candidate.publishedAt()));
  }

  private CollectedContentItem toItem(SocialPostCandidate candidate) {
    return new CollectedContentItem(
        candidate.title(), "", candidate.url(), null, parseDate(candidate.publishedDate()));
  }

  private LocalDate parseDateTime(String value) {
    try {
      return Instant.parse(value).atZone(COLLECTION_ZONE).toLocalDate();
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      return null;
    }
  }
}
