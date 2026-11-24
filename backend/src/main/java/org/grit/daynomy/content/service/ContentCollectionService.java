package org.grit.daynomy.content.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
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
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
@Service
public class ContentCollectionService {

  private static final ZoneId COLLECTION_ZONE = ZoneId.of("Asia/Seoul");

  private final ContentCollectionSourceRepository sourceRepository;
  private final YouTubeClient youtubeClient;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;
  private final ContentCollectionPersistenceService persistenceService;

  @Value("${content.collection.lookback-days:3}")
  private int configuredLookbackDays;

  @Transactional
  public ContentCollectionResultResponse collect(Long sourceId) {
    ContentCollectionSource source = getSource(sourceId);
    Instant now = Instant.now();
    int lookbackDays = Math.min(31, Math.max(1, configuredLookbackDays));
    Instant from = now.minus(java.time.Duration.ofDays(lookbackDays));
    List<CollectedContentItem> items =
        switch (source.getSourceType()) {
          case YOUTUBE -> collectYouTube(sourceId, source.getSourceUrl(), from, now);
          case X, THREADS -> collectSocial(sourceId, source);
          default ->
              throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_INVALID);
        };
    return persistenceService.persist(sourceId, items, now);
  }

  private ContentCollectionSource getSource(Long sourceId) {
    return sourceRepository
        .findByIdForUpdate(sourceId)
        .orElseThrow(
            () -> new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));
  }

  private List<CollectedContentItem> collectYouTube(
      Long sourceId, String sourceUrl, Instant from, Instant to) {
    List<CollectedContentItem> items = new ArrayList<>();
    int invalidDateCount = 0;
    for (YouTubeVideoCandidate candidate : youtubeClient.listChannelUploads(sourceUrl, from, to)) {
      CollectedContentItem item = toItem(candidate);
      items.add(item);
      if (hasText(candidate.publishedAt()) && item.publishedDate() == null) {
        invalidDateCount++;
      }
    }
    logInvalidDates(sourceId, "YOUTUBE", invalidDateCount);
    return List.copyOf(items);
  }

  private List<CollectedContentItem> collectSocial(Long sourceId, ContentCollectionSource source) {
    String xUrl =
        source.getSourceType() == org.grit.daynomy.content.domain.ContentSourceType.X
            ? source.getSourceUrl()
            : null;
    String threadsUrl =
        source.getSourceType() == org.grit.daynomy.content.domain.ContentSourceType.THREADS
            ? source.getSourceUrl()
            : null;
    List<CollectedContentItem> items = new ArrayList<>();
    int invalidDateCount = 0;
    for (SocialPostCandidate candidate :
        socialPostSearchClient.searchSourcePosts(source.getName(), xUrl, threadsUrl)) {
      CollectedContentItem item = toItem(candidate);
      items.add(item);
      if (hasText(candidate.publishedDate()) && item.publishedDate() == null) {
        invalidDateCount++;
      }
    }
    logInvalidDates(sourceId, source.getSourceType().name(), invalidDateCount);
    return List.copyOf(items);
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
    if (!hasText(value)) {
      return null;
    }
    try {
      return Instant.parse(value).atZone(COLLECTION_ZONE).toLocalDate();
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private LocalDate parseDate(String value) {
    if (!hasText(value)) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private void logInvalidDates(Long sourceId, String provider, int invalidDateCount) {
    if (invalidDateCount == 0) {
      return;
    }
    log.atWarn()
        .addKeyValue("event", LogEvent.CONTENT_COLLECTION_DATE_PARSE_FAILED.code())
        .addKeyValue("sourceId", sourceId)
        .addKeyValue("provider", provider)
        .addKeyValue("invalidDateCount", invalidDateCount)
        .log(LogEvent.CONTENT_COLLECTION_DATE_PARSE_FAILED.message());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
