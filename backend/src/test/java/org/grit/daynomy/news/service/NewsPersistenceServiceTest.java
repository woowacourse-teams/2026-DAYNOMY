package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.repository.NewsGenerationTopicRepository;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsPersistenceServiceTest {

  @Mock private NewsRepository newsRepository;
  @Mock private NewsGenerationTopicRepository topicRepository;

  @InjectMocks private NewsPersistenceService newsPersistenceService;

  @Test
  @DisplayName("경제 뉴스 초안을 저장한다")
  void saveDraftsSavesNews() {
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("a".repeat(64), "제목", "본문", Category.STOCK, List.of());
    UUID reservationToken = UUID.randomUUID();
    NewsGenerationTopic topic = org.mockito.Mockito.mock(NewsGenerationTopic.class);
    given(topic.getTopicKey()).willReturn("a".repeat(64));
    given(topic.isReservedBy(reservationToken)).willReturn(true);
    given(topicRepository.findAllByIdForUpdate(List.of(1L))).willReturn(List.of(topic));
    given(newsRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    List<News> drafts =
        newsPersistenceService.saveDrafts(List.of(article), List.of(1L), reservationToken);

    ArgumentCaptor<List<News>> newsCaptor = ArgumentCaptor.forClass(List.class);
    verify(newsRepository).saveAll(newsCaptor.capture());
    assertThat(drafts).hasSize(1);
    assertThat(newsCaptor.getValue().getFirst().getTitle()).isEqualTo("제목");
    assertThat(newsCaptor.getValue().getFirst().getSources()).isEmpty();
    assertThat(newsCaptor.getValue().getFirst().getImageUrl()).isNull();
    verify(topic).generated(drafts.getFirst(), reservationToken);
  }

  @Test
  @DisplayName("예약 소유권을 잃은 요청은 뉴스를 저장하지 않는다")
  void saveDraftsRejectsLostReservationOwnership() {
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("a".repeat(64), "제목", "본문", Category.STOCK, List.of());
    UUID reservationToken = UUID.randomUUID();
    NewsGenerationTopic topic = org.mockito.Mockito.mock(NewsGenerationTopic.class);
    given(topicRepository.findAllByIdForUpdate(List.of(1L))).willReturn(List.of(topic));

    assertThatThrownBy(
            () ->
                newsPersistenceService.saveDrafts(List.of(article), List.of(1L), reservationToken))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("News topic reservation ownership was lost.");

    verify(newsRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("생성된 뉴스와 이미지 URL 개수가 다르면 저장하지 않는다")
  void saveDraftsRejectsMismatchedImageUrls() {
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("제목", "본문", Category.STOCK, List.of());

    assertThatThrownBy(() -> newsPersistenceService.saveDrafts(List.of(article), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Each generated news article must have an image URL slot.");

    verifyNoInteractions(newsRepository);
  }
}
