package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.external.dart.DartNewsPromptService;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
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
class NewsGenerationServiceTest {

  @Mock private DartNewsPromptService dartNewsPromptService;
  @Mock private OpenAiNewsGenerator openAiNewsGenerator;
  @Mock private NewsRepository newsRepository;

  @InjectMocks private NewsGenerationService newsGenerationService;

  @Test
  @DisplayName("DART 프롬프트로 뉴스를 생성하고 저장한다")
  void generateDartNewsSavesGeneratedNews() {
    LocalDate beginDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 17);
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 0, 0),
            "prompt");
    given(dartNewsPromptService.createPrompts(beginDate, endDate, "B", "K"))
        .willReturn(List.of(prompt));
    given(newsRepository.existsBySourceAndExternalId(NewsSource.DART, "20260817000001"))
        .willReturn(false);
    given(openAiNewsGenerator.generate(prompt)).willReturn(new GeneratedNews("제목", "요약", "본문"));

    int savedCount = newsGenerationService.generateDartNews(beginDate, endDate, "B", "K");

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    assertThat(savedCount).isEqualTo(1);
    assertThat(newsCaptor.getValue().getTitle()).isEqualTo("제목");
    assertThat(newsCaptor.getValue().getExternalId()).isEqualTo("20260817000001");
    assertThat(newsCaptor.getValue().getSourceUrl()).isEqualTo("https://dart.example/1");
  }

  @Test
  @DisplayName("이미 저장된 DART 공시는 뉴스 생성을 건너뛴다")
  void generateDartNewsSkipsExistingNews() {
    LocalDate beginDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 17);
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 0, 0),
            "prompt");
    given(dartNewsPromptService.createPrompts(beginDate, endDate, "B", "K"))
        .willReturn(List.of(prompt));
    given(newsRepository.existsBySourceAndExternalId(NewsSource.DART, "20260817000001"))
        .willReturn(true);

    int savedCount = newsGenerationService.generateDartNews(beginDate, endDate, "B", "K");

    assertThat(savedCount).isZero();
    verify(openAiNewsGenerator, never()).generate(prompt);
    verify(newsRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
