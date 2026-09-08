package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
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
  @Mock private KeywordService keywordService;
  @Mock private MarketAnalysisService marketAnalysisService;

  @InjectMocks private NewsPersistenceService newsPersistenceService;

  @Test
  @DisplayName("생성된 뉴스 정보를 저장한다")
  void saveSavesNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            "DART",
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    GeneratedNews generatedNews = new GeneratedNews("제목", "본문");
    List<NewsKeyword> keywords = List.of(mock(NewsKeyword.class));
    NewsMarketAnalysis marketAnalysis = mock(NewsMarketAnalysis.class);
    newsPersistenceService.save(prompt, generatedNews, "image.png", keywords, marketAnalysis);

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    assertThat(newsCaptor.getValue().getTitle()).isEqualTo("제목");
    assertThat(newsCaptor.getValue().getSources())
        .containsExactly(new NewsSourceInfo("DART", "https://dart.example/1"));
    assertThat(newsCaptor.getValue().getImageUrl()).isEqualTo("image.png");
    verify(keywordService).saveKeywords(newsCaptor.getValue(), keywords);
    verify(marketAnalysisService).saveMarketAnalysis(newsCaptor.getValue(), marketAnalysis);
  }

  @Test
  @DisplayName("같은 출처의 뉴스도 생성 결과를 저장한다")
  void savePersistsNewsWithoutSourceIdentityCheck() {
    NewsPrompt prompt =
        new NewsPrompt(
            "DART",
            "20260817000001",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    newsPersistenceService.save(
        prompt,
        new GeneratedNews("제목", "본문"),
        "image.png",
        List.of(),
        mock(NewsMarketAnalysis.class));

    verify(newsRepository).save(org.mockito.ArgumentMatchers.any());
    verify(keywordService).saveKeywords(org.mockito.ArgumentMatchers.any(), eq(List.of()));
    verify(marketAnalysisService)
        .saveMarketAnalysis(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
