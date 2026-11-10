package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.external.openai.EconomicNewsResearch;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.repository.NewsGenerationTopicRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsGenerationTopicServiceTest {

  @Mock private NewsGenerationTopicRepository topicRepository;

  @InjectMocks private NewsGenerationTopicService topicService;

  @Test
  @DisplayName("예약 전에 만료된 예약을 복구하고 새 소유권을 기록한다")
  void reserveReadyRecoversExpiredReservationsAndAssignsOwner() {
    NewsGenerationTopic topic = topic();
    UUID reservationToken = UUID.randomUUID();
    given(topicRepository.findReadyForUpdate(eq("KOREA"), any(LocalDate.class), eq(1)))
        .willReturn(List.of(topic));

    List<NewsGenerationTopic> reserved =
        topicService.reserveReady(NewsGenerationType.KOREA, 1, reservationToken);

    verify(topicRepository).releaseExpiredReservations(eq("KOREA"), any(Instant.class));
    assertThat(reserved).containsExactly(topic);
    assertThat(topic.isReservedBy(reservationToken)).isTrue();
  }

  @Test
  @DisplayName("출처 순서와 후행 슬래시가 달라도 같은 주제 키로 등록한다")
  void registerUsesStableSourceFingerprint() {
    EconomicNewsResearch first =
        research(
            "첫 번째 표현",
            List.of(
                new NewsSourceInfo("출처 1", "https://EXAMPLE.com/news/1/"),
                new NewsSourceInfo("출처 2", "https://example.com/news/2")));
    EconomicNewsResearch second =
        research(
            "두 번째 표현",
            List.of(
                new NewsSourceInfo("출처 2", "https://example.com/news/2"),
                new NewsSourceInfo("출처 1", "https://example.com/news/1")));
    given(topicRepository.findAllByTopicKeyIn(anyCollection())).willReturn(List.of());
    given(topicRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    List<NewsGenerationTopic> saved =
        topicService.register(NewsGenerationType.KOREA, List.of(first, second));

    assertThat(saved).hasSize(1);
    ArgumentCaptor<List<NewsGenerationTopic>> captor = ArgumentCaptor.forClass(List.class);
    verify(topicRepository).saveAll(captor.capture());
    assertThat(captor.getValue().getFirst().getTopicKey()).hasSize(64);
  }

  private EconomicNewsResearch research(String title, List<NewsSourceInfo> sources) {
    return new EconomicNewsResearch(
        title, LocalDate.of(2026, 11, 6), Category.STOCK, List.of("사실 1", "사실 2", "사실 3"), sources);
  }

  private NewsGenerationTopic topic() {
    return NewsGenerationTopic.create(
        "a".repeat(64),
        NewsGenerationType.KOREA,
        "경제 뉴스",
        LocalDate.of(2026, 11, 6),
        Category.STOCK,
        List.of("사실 1", "사실 2", "사실 3"),
        List.of(
            new NewsSourceInfo("출처 1", "https://example.com/1"),
            new NewsSourceInfo("출처 2", "https://example.com/2")));
  }
}
