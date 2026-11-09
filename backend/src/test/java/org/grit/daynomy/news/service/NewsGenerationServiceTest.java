package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.grit.daynomy.external.openai.EconomicNewsResearch;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.external.openai.OpenAiNewsTopicDeduplicator;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.ai.NewsGenerationTopicResearch;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsGenerationServiceTest {

  @Mock private OpenAiNewsGenerator openAiNewsGenerator;
  @Mock private OpenAiNewsTopicDeduplicator topicDeduplicator;
  @Mock private NewsGenerationTopicService topicService;
  @Mock private NewsGenerationDiscoveryLock discoveryLock;
  @Mock private NewsPersistenceService newsPersistenceService;

  @InjectMocks private NewsGenerationService newsGenerationService;

  @Test
  @DisplayName("입력한 키워드로 경제 뉴스 초안을 생성한다")
  void generateEconomicNewsDraftByKeyword() {
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("keyword", "미국 중간선거", "본문", Category.STOCK, List.of());
    News savedNews = org.mockito.Mockito.mock(News.class);
    given(openAiNewsGenerator.generateEconomicNewsByKeyword("미국 중간선거"))
        .willReturn(List.of(article));
    given(newsPersistenceService.saveDrafts(List.of(article))).willReturn(List.of(savedNews));

    List<News> drafts = newsGenerationService.generateEconomicNewsDraftByKeyword("미국 중간선거");

    assertThat(drafts).containsExactly(savedNews);
    verify(openAiNewsGenerator).generateEconomicNewsByKeyword("미국 중간선거");
    verify(newsPersistenceService).saveDrafts(List.of(article));
    verifyNoInteractions(topicService, topicDeduplicator, discoveryLock);
  }

  @Test
  @DisplayName("준비된 주제를 예약해 한국 뉴스 초안을 생성한다")
  void generateKoreanEconomicNewsDraftsUsesReadyTopics() {
    List<NewsGenerationTopic> topics =
        List.of(topic(1L, "a"), topic(2L, "b"), topic(3L, "c"), topic(4L, "d"));
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("a".repeat(64), "한국 경제 뉴스", "본문", Category.STOCK, List.of());
    News savedNews = org.mockito.Mockito.mock(News.class);
    given(topicService.reserveReady(eq(NewsGenerationType.KOREA), eq(5), any(UUID.class)))
        .willReturn(topics);
    given(openAiNewsGenerator.write(topics.stream().map(NewsGenerationTopic::toResearch).toList()))
        .willReturn(List.of(article));
    given(
            newsPersistenceService.saveDrafts(
                eq(List.of(article)), eq(List.of(1L, 2L, 3L, 4L)), any(UUID.class)))
        .willReturn(List.of(savedNews));

    List<News> drafts = newsGenerationService.generateKoreanEconomicNewsDrafts();

    assertThat(drafts).containsExactly(savedNews);
    verify(openAiNewsGenerator, never()).researchEconomicNews();
    verify(topicDeduplicator, never()).removeDuplicates(any(), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("준비된 주제가 부족하면 신규 주제를 조사하고 중복을 제거한다")
  void generateKoreanEconomicNewsDraftsDiscoversUniqueTopics() {
    EconomicNewsResearch research = research("새 경제 뉴스");
    NewsGenerationTopic topic = topic(1L, "a");
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("a".repeat(64), "새 경제 뉴스", "본문", Category.STOCK, List.of());
    given(
            topicService.reserveReady(
                eq(NewsGenerationType.KOREA), any(Integer.class), any(UUID.class)))
        .willReturn(List.of(), List.of(), List.of(topic));
    given(discoveryLock.withLock(any(), any()))
        .willAnswer(
            invocation ->
                Optional.of(
                    ((Supplier<List<NewsGenerationTopic>>) invocation.getArgument(1)).get()));
    given(topicService.findRecent(any(), any(), any(Integer.class))).willReturn(List.of());
    given(openAiNewsGenerator.researchEconomicNews()).willReturn(List.of(research));
    given(topicDeduplicator.removeDuplicates(List.of(research), List.of()))
        .willReturn(List.of(research));
    given(openAiNewsGenerator.write(List.of(topic.toResearch()))).willReturn(List.of(article));
    given(newsPersistenceService.saveDrafts(eq(List.of(article)), eq(List.of(1L)), any(UUID.class)))
        .willReturn(List.of());

    newsGenerationService.generateKoreanEconomicNewsDrafts();

    verify(topicService).register(NewsGenerationType.KOREA, List.of(research));
    verify(openAiNewsGenerator).researchEconomicNews();
    verify(openAiNewsGenerator, never()).researchUsEconomicNews();
  }

  @Test
  @DisplayName("뉴스 생성 실패 시 예약한 주제를 준비 상태로 복구한다")
  void generationFailureReleasesReservedTopics() {
    List<NewsGenerationTopic> topics =
        List.of(topic(1L, "a"), topic(2L, "b"), topic(3L, "c"), topic(4L, "d"));
    List<NewsGenerationTopicResearch> research =
        topics.stream().map(NewsGenerationTopic::toResearch).toList();
    RuntimeException failure = new RuntimeException("generation failed");
    given(topicService.reserveReady(eq(NewsGenerationType.US), eq(5), any(UUID.class)))
        .willReturn(topics);
    given(openAiNewsGenerator.write(research)).willThrow(failure);

    assertThatThrownBy(newsGenerationService::generateUsEconomicNewsDrafts).isSameAs(failure);

    verify(topicService).release(eq(List.of(1L, 2L, 3L, 4L)), any(UUID.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("신규 주제 조사 실패 시 수집 중 예약한 주제도 복구한다")
  void discoveryFailureReleasesTopicsReservedInsideLock() {
    NewsGenerationTopic topic = topic(1L, "a");
    RuntimeException failure = new RuntimeException("research failed");
    given(
            topicService.reserveReady(
                eq(NewsGenerationType.KOREA), any(Integer.class), any(UUID.class)))
        .willReturn(List.of(), List.of(topic));
    given(discoveryLock.withLock(any(), any()))
        .willAnswer(
            invocation ->
                Optional.of(
                    ((Supplier<List<NewsGenerationTopic>>) invocation.getArgument(1)).get()));
    given(topicService.findRecent(any(), any(), any(Integer.class))).willReturn(List.of());
    given(openAiNewsGenerator.researchEconomicNews()).willThrow(failure);

    assertThatThrownBy(newsGenerationService::generateKoreanEconomicNewsDrafts).isSameAs(failure);

    verify(topicService).release(eq(List.of(1L)), any(UUID.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("잠금 정리 실패 시 잠금 안에서 추가 예약한 주제도 복구한다")
  void lockCleanupFailureReleasesTopicsReservedInsideLock() {
    List<NewsGenerationTopic> topics =
        List.of(topic(1L, "a"), topic(2L, "b"), topic(3L, "c"), topic(4L, "d"));
    RuntimeException failure = new RuntimeException("unlock failed");
    given(
            topicService.reserveReady(
                eq(NewsGenerationType.KOREA), any(Integer.class), any(UUID.class)))
        .willReturn(List.of(), topics);
    given(discoveryLock.withLock(any(), any()))
        .willAnswer(
            invocation -> {
              ((Supplier<List<NewsGenerationTopic>>) invocation.getArgument(1)).get();
              throw failure;
            });

    assertThatThrownBy(newsGenerationService::generateKoreanEconomicNewsDrafts).isSameAs(failure);

    verify(topicService).release(eq(List.of(1L, 2L, 3L, 4L)), any(UUID.class));
  }

  private NewsGenerationTopic topic(Long id, String keyCharacter) {
    NewsGenerationTopic topic = org.mockito.Mockito.mock(NewsGenerationTopic.class);
    String topicKey = keyCharacter.repeat(64);
    NewsGenerationTopicResearch research =
        new NewsGenerationTopicResearch(
            topicKey,
            "뉴스 " + keyCharacter,
            LocalDate.of(2026, 11, 6),
            Category.STOCK,
            List.of("사실 1", "사실 2", "사실 3"),
            List.of(
                new NewsSourceInfo("출처 1", "https://example.com/1"),
                new NewsSourceInfo("출처 2", "https://example.com/2")));
    given(topic.getId()).willReturn(id);
    org.mockito.Mockito.lenient().when(topic.toResearch()).thenReturn(research);
    return topic;
  }

  private EconomicNewsResearch research(String title) {
    return new EconomicNewsResearch(
        title,
        LocalDate.of(2026, 11, 6),
        Category.STOCK,
        List.of("사실 1", "사실 2", "사실 3"),
        List.of(
            new NewsSourceInfo("출처 1", "https://example.com/1"),
            new NewsSourceInfo("출처 2", "https://example.com/2")));
  }
}
