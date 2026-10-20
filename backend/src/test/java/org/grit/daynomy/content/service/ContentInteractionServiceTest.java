package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentInteractionType;
import org.grit.daynomy.content.domain.ContentType;
import org.grit.daynomy.content.dto.ContentInteractionRequest;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.content.repository.ContentInteractionRepository;
import org.grit.daynomy.content.repository.ContentPopularityProjection;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentInteractionServiceTest {

  @Mock private ContentInteractionRepository interactionRepository;
  @Mock private NewsRepository newsRepository;
  @Mock private FeaturedPersonPostRepository personPostRepository;
  @Mock private AssetContentRepository assetContentRepository;

  private ContentInteractionService service;

  @BeforeEach
  void setUp() {
    service =
        new ContentInteractionService(
            interactionRepository,
            newsRepository,
            personPostRepository,
            assetContentRepository,
            Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void recordsInteractionForPublishedNews() {
    UUID eventId = UUID.randomUUID();
    UUID visitorId = UUID.randomUUID();
    given(newsRepository.existsByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(true);

    service.record(
        new ContentInteractionRequest(
            eventId, visitorId, ContentType.NEWS, 1L, ContentInteractionType.OPEN));

    then(interactionRepository)
        .should()
        .insertIfAbsent(
            eq(eventId),
            argThat(value -> value.matches("[0-9a-f]{64}")),
            eq("NEWS"),
            eq(1L),
            eq("OPEN"),
            eq(LocalDate.of(2026, 10, 20)),
            eq(Instant.parse("2026-10-20T03:00:00Z")));
  }

  @Test
  void rejectsInteractionForUnavailableContent() {
    given(newsRepository.existsByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(false);
    ContentInteractionRequest request =
        new ContentInteractionRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ContentType.NEWS,
            1L,
            ContentInteractionType.OPEN);

    assertThatThrownBy(() -> service.record(request))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ContentErrorCode.CONTENT_NOT_AVAILABLE);

    then(interactionRepository).shouldHaveNoInteractions();
  }

  @Test
  void returnsSevenDayPopularityScores() {
    ContentPopularityProjection projection = mock(ContentPopularityProjection.class);
    given(projection.getContentType()).willReturn("NEWS");
    given(projection.getContentId()).willReturn(7L);
    given(projection.getScore()).willReturn(5L);
    given(projection.getLastInteractedAt()).willReturn(Instant.parse("2026-10-20T02:00:00Z"));
    given(interactionRepository.findPopularitySince(LocalDate.of(2026, 10, 14)))
        .willReturn(List.of(projection));

    var response = service.getPopularity(7);

    assertThat(response.periodDays()).isEqualTo(7);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().contentType()).isEqualTo(ContentType.NEWS);
    assertThat(response.items().getFirst().score()).isEqualTo(5L);
  }
}
