package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.bok.BokNewsPromptService;
import org.grit.daynomy.external.dart.DartNewsPromptService;
import org.grit.daynomy.external.kosis.KosisNewsPromptService;
import org.grit.daynomy.external.openai.OpenAiImageGenerator;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.keyword.ai.KeywordAiClient;
import org.grit.daynomy.market.ai.MarketAnalysisAiClient;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsGenerationServiceTest {

  private static final byte[] IMAGE_BYTES = {1, 2, 3};
  private static final String IMAGE_URL = "https://example.com/daynomy/news-image.webp";

  @Mock private DartNewsPromptService dartNewsPromptService;
  @Mock private KosisNewsPromptService kosisNewsPromptService;
  @Mock private BokNewsPromptService bokNewsPromptService;
  @Mock private OpenAiNewsGenerator openAiNewsGenerator;
  @Mock private OpenAiImageGenerator openAiImageGenerator;
  @Mock private S3ImageStorage s3ImageStorage;
  @Mock private KeywordAiClient keywordAiClient;
  @Mock private MarketAnalysisAiClient marketAnalysisAiClient;
  @Mock private NewsPersistenceService newsPersistenceService;

  @InjectMocks private NewsGenerationService newsGenerationService;

  @Test
  @DisplayName("예약 DART 뉴스 생성은 유가증권과 코스닥 주요사항보고서를 실행한다")
  void generateScheduledDartNewsRunsForKospiAndKosdaq() {
    given(
            dartNewsPromptService.createPrompts(
                any(LocalDate.class), any(LocalDate.class), eq("B"), eq("Y")))
        .willReturn(List.of());
    given(
            dartNewsPromptService.createPrompts(
                any(LocalDate.class), any(LocalDate.class), eq("B"), eq("K")))
        .willReturn(List.of());

    int savedCount = newsGenerationService.generateScheduledDartNews();

    assertThat(savedCount).isZero();
    verify(dartNewsPromptService)
        .createPrompts(any(LocalDate.class), any(LocalDate.class), eq("B"), eq("Y"));
    verify(dartNewsPromptService)
        .createPrompts(any(LocalDate.class), any(LocalDate.class), eq("B"), eq("K"));
  }

  @Test
  @DisplayName("DART 프롬프트로 뉴스를 생성하고 저장한다")
  void generateDartNewsSavesGeneratedNews() {
    LocalDate beginDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 17);
    NewsPrompt prompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("DART", "https://dart.example/1")),
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    given(dartNewsPromptService.createPrompts(beginDate, endDate, "B", "K"))
        .willReturn(List.of(prompt));
    given(openAiNewsGenerator.generate(prompt)).willReturn(new GeneratedNews("제목", "본문"));
    given(openAiImageGenerator.generateNewsImage("제목")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");
    int savedCount = newsGenerationService.generateDartNews(beginDate, endDate, "B", "K");

    verify(keywordAiClient).extractKeywords("본문");
    verify(marketAnalysisAiClient).analyze("본문");
    verify(s3ImageStorage).upload(eq(IMAGE_BYTES), eq("webp"), eq("image/webp"));
    verify(newsPersistenceService)
        .save(prompt, new GeneratedNews("제목", "본문"), IMAGE_URL, List.of(), marketAnalysis);
    assertThat(savedCount).isEqualTo(1);
  }

  @Test
  @DisplayName("AI 생성에 실패한 공시는 건너뛰고 다음 공시를 계속 처리한다")
  void generateDartNewsSkipsAiFailureAndContinues() {
    LocalDate date = LocalDate.of(2026, 8, 17);
    NewsPrompt failedPrompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("DART", "https://dart.example/failed")),
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "failed prompt");
    NewsPrompt successfulPrompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("DART", "https://dart.example/successful")),
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "successful prompt");
    GeneratedNews generatedNews = new GeneratedNews("제목", "본문");
    given(dartNewsPromptService.createPrompts(date, date, "B", "K"))
        .willReturn(List.of(failedPrompt, successfulPrompt));
    given(openAiNewsGenerator.generate(failedPrompt))
        .willThrow(new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED));
    given(openAiNewsGenerator.generate(successfulPrompt)).willReturn(generatedNews);
    given(openAiImageGenerator.generateNewsImage("제목")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");
    int savedCount = newsGenerationService.generateDartNews(date, date, "B", "K");

    assertThat(savedCount).isEqualTo(1);
    verify(newsPersistenceService)
        .save(successfulPrompt, generatedNews, IMAGE_URL, List.of(), marketAnalysis);
    verify(openAiImageGenerator).generateNewsImage("제목");
  }

  @Test
  @DisplayName("동일한 프롬프트도 생성 결과를 저장한다")
  void generateDartNewsSavesWithoutSourceIdentityCheck() {
    LocalDate beginDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 17);
    NewsPrompt prompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("DART", "https://dart.example/1")),
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");
    given(dartNewsPromptService.createPrompts(beginDate, endDate, "B", "K"))
        .willReturn(List.of(prompt));
    given(openAiNewsGenerator.generate(prompt)).willReturn(new GeneratedNews("제목", "본문"));
    given(openAiImageGenerator.generateNewsImage("제목")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");

    int savedCount = newsGenerationService.generateDartNews(beginDate, endDate, "B", "K");

    assertThat(savedCount).isEqualTo(1);
    verify(newsPersistenceService)
        .save(prompt, new GeneratedNews("제목", "본문"), IMAGE_URL, List.of(), marketAnalysis);
  }

  @Test
  @DisplayName("KOSIS 프롬프트로 뉴스를 생성하고 저장한다")
  void generateKosisNewsSavesGeneratedNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("KOSIS", "https://kosis.kr")),
            Category.STOCK,
            Instant.parse("2026-08-18T00:00:00Z"),
            "prompt");
    given(kosisNewsPromptService.createPrompts()).willReturn(List.of(prompt));
    given(openAiNewsGenerator.generate(prompt)).willReturn(new GeneratedNews("물가 뉴스", "본문"));
    given(openAiImageGenerator.generateNewsImage("물가 뉴스")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");
    int savedCount = newsGenerationService.generateKosisNews();

    verify(newsPersistenceService)
        .save(prompt, new GeneratedNews("물가 뉴스", "본문"), IMAGE_URL, List.of(), marketAnalysis);
    assertThat(savedCount).isEqualTo(1);
  }

  @Test
  @DisplayName("생성 결과를 저장하면 저장 건수에 포함한다")
  void generateKosisNewsIncludesSavedNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("KOSIS", "https://kosis.kr")),
            Category.STOCK,
            Instant.parse("2026-08-18T00:00:00Z"),
            "prompt");
    GeneratedNews generatedNews = new GeneratedNews("물가 뉴스", "본문");
    given(kosisNewsPromptService.createPrompts()).willReturn(List.of(prompt));
    given(openAiNewsGenerator.generate(prompt)).willReturn(generatedNews);
    given(openAiImageGenerator.generateNewsImage("물가 뉴스")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");
    int savedCount = newsGenerationService.generateKosisNews();

    assertThat(savedCount).isEqualTo(1);
    verify(newsPersistenceService)
        .save(prompt, generatedNews, IMAGE_URL, List.of(), marketAnalysis);
  }

  @Test
  @DisplayName("한국은행 ECOS 프롬프트로 뉴스를 생성하고 저장한다")
  void generateBokNewsSavesGeneratedNews() {
    NewsPrompt prompt =
        new NewsPrompt(
            List.of(new NewsSourceInfo("한국은행", "https://ecos.bok.or.kr")),
            Category.STOCK,
            Instant.parse("2026-08-18T00:00:00Z"),
            "prompt");
    given(bokNewsPromptService.createPrompts()).willReturn(List.of(prompt));
    given(openAiNewsGenerator.generate(prompt)).willReturn(new GeneratedNews("금리 뉴스", "본문"));
    given(openAiImageGenerator.generateNewsImage("금리 뉴스")).willReturn(IMAGE_BYTES);
    stubImageUpload();
    NewsMarketAnalysis marketAnalysis = stubAnalyses("본문");
    int savedCount = newsGenerationService.generateBokNews();

    verify(newsPersistenceService)
        .save(prompt, new GeneratedNews("금리 뉴스", "본문"), IMAGE_URL, List.of(), marketAnalysis);
    assertThat(savedCount).isEqualTo(1);
  }

  private NewsMarketAnalysis stubAnalyses(String newsContent) {
    given(keywordAiClient.extractKeywords(newsContent)).willReturn(List.of());
    NewsMarketAnalysis marketAnalysis = mock(NewsMarketAnalysis.class);
    given(marketAnalysisAiClient.analyze(newsContent)).willReturn(marketAnalysis);
    return marketAnalysis;
  }

  private void stubImageUpload() {
    given(s3ImageStorage.upload(any(), eq("webp"), eq("image/webp")))
        .willReturn(new S3ImageStorage.StoredImage("news-image.webp", IMAGE_URL));
  }
}
