package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.openai.SocialPostCandidate;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentCollectionServiceTest {

  @Mock private ContentCollectionSourceRepository sourceRepository;
  @Mock private YouTubeClient youtubeClient;
  @Mock private OpenAiSocialPostSearchClient socialPostSearchClient;
  @Mock private ContentCollectionPersistenceService persistenceService;

  private final Logger logger = (Logger) LoggerFactory.getLogger(ContentCollectionService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level originalLevel;

  @BeforeEach
  void setUp() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void youtubeCollectionAlwaysUsesConfiguredLookbackInsteadOfLastCollectionTime() {
    ContentCollectionSource source =
        new ContentCollectionSource(
            "경제 채널", ContentSourceType.YOUTUBE, "https://www.youtube.com/@economy", null, true);
    source.completeCollection(Instant.parse("2026-01-01T00:00:00Z"));
    given(sourceRepository.findByIdForUpdate(1L)).willReturn(Optional.of(source));
    given(youtubeClient.listChannelUploads(eq(source.getSourceUrl()), any(), any()))
        .willReturn(List.of());
    given(persistenceService.persist(eq(1L), eq(List.of()), any()))
        .willReturn(new ContentCollectionResultResponse(1L, 0, 0, 0, 0));
    ContentCollectionService service =
        new ContentCollectionService(
            sourceRepository, youtubeClient, socialPostSearchClient, persistenceService);
    ReflectionTestUtils.setField(service, "configuredLookbackDays", 3);

    service.collect(1L);

    then(sourceRepository).should().findByIdForUpdate(1L);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
    then(youtubeClient)
        .should()
        .listChannelUploads(eq(source.getSourceUrl()), from.capture(), to.capture());
    assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofDays(3));
    assertThat(from.getValue()).isNotEqualTo(source.getLastCollectedAt());
  }

  @Test
  void invalidPublishedDatesAreAggregatedWithoutLoggingRawValues() {
    ContentCollectionSource source =
        new ContentCollectionSource(
            "경제 계정", ContentSourceType.X, "https://x.com/economy", null, true);
    given(sourceRepository.findByIdForUpdate(1L)).willReturn(Optional.of(source));
    given(socialPostSearchClient.searchSourcePosts(source.getName(), source.getSourceUrl(), null))
        .willReturn(
            List.of(
                new SocialPostCandidate(
                    SocialPostCandidate.Platform.X,
                    "시장 소식",
                    "https://x.com/economy/status/1",
                    "private-invalid-date")));
    given(persistenceService.persist(eq(1L), any(), any()))
        .willReturn(new ContentCollectionResultResponse(1L, 1, 1, 0, 0));
    ContentCollectionService service =
        new ContentCollectionService(
            sourceRepository, youtubeClient, socialPostSearchClient, persistenceService);
    ReflectionTestUtils.setField(service, "configuredLookbackDays", 3);

    service.collect(1L);

    ILoggingEvent event = logEvent(LogEvent.CONTENT_COLLECTION_DATE_PARSE_FAILED);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(keyValues(event))
        .containsEntry("sourceId", 1L)
        .containsEntry("provider", "X")
        .containsEntry("invalidDateCount", 1);
    assertThat(event.getFormattedMessage()).doesNotContain("private-invalid-date");
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
  }
}
