package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
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

  @InjectMocks private NewsPersistenceService newsPersistenceService;

  @Test
  @DisplayName("새 뉴스이면 생성된 뉴스 정보를 저장한다")
  void saveIfAbsentSavesNewNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    GeneratedNews generatedNews = new GeneratedNews("제목", "요약", "본문");
    given(newsRepository.existsBySourceAndExternalId(NewsSource.DART, "20260817000001"))
        .willReturn(false);

    boolean saved = newsPersistenceService.saveIfAbsent(prompt, generatedNews, "image.png");

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    assertThat(saved).isTrue();
    assertThat(newsCaptor.getValue().getTitle()).isEqualTo("제목");
    assertThat(newsCaptor.getValue().getExternalId()).isEqualTo("20260817000001");
    assertThat(newsCaptor.getValue().getSourceUrl()).isEqualTo("https://dart.example/1");
    assertThat(newsCaptor.getValue().getImageUrl()).isEqualTo("image.png");
  }

  @Test
  @DisplayName("이미 저장된 뉴스는 저장하지 않는다")
  void saveIfAbsentSkipsExistingNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    given(newsRepository.existsBySourceAndExternalId(NewsSource.DART, "20260817000001"))
        .willReturn(true);

    boolean saved =
        newsPersistenceService.saveIfAbsent(
            prompt, new GeneratedNews("제목", "요약", "본문"), "image.png");

    assertThat(saved).isFalse();
    verify(newsRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
