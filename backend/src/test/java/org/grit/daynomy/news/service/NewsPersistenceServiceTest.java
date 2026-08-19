package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class NewsPersistenceServiceTest {

  @Mock private NewsRepository newsRepository;

  @InjectMocks private NewsPersistenceService newsPersistenceService;

  @Test
  @DisplayName("뉴스 저장은 트랜잭션 안에서 새 뉴스만 저장한다")
  void saveIfAbsentSavesNewNews() throws NoSuchMethodException {
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 0, 0),
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
    assertThat(
            NewsPersistenceService.class
                .getMethod("saveIfAbsent", NewsPrompt.class, GeneratedNews.class, String.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
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
            LocalDateTime.of(2026, 8, 17, 0, 0),
            "prompt");
    given(newsRepository.existsBySourceAndExternalId(NewsSource.DART, "20260817000001"))
        .willReturn(true);

    boolean saved =
        newsPersistenceService.saveIfAbsent(prompt, new GeneratedNews("제목", "요약", "본문"), "image.png");

    assertThat(saved).isFalse();
    verify(newsRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
