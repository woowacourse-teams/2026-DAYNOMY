package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentCollectionServiceTest {

  @Mock private ContentCollectionSourceRepository sourceRepository;
  @Mock private YouTubeClient youtubeClient;
  @Mock private OpenAiSocialPostSearchClient socialPostSearchClient;
  @Mock private ContentCollectionPersistenceService persistenceService;

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
}
