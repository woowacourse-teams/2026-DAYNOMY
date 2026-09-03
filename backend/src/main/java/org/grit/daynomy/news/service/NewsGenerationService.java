package org.grit.daynomy.news.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class NewsGenerationService {

  private static final String MAJOR_REPORT = "B";
  private static final String KOSPI = "Y";
  private static final String KOSDAQ = "K";

  private final DartNewsPromptService dartNewsPromptService;
  private final KosisNewsPromptService kosisNewsPromptService;
  private final BokNewsPromptService bokNewsPromptService;
  private final OpenAiNewsGenerator openAiNewsGenerator;
  private final OpenAiImageGenerator openAiImageGenerator;
  private final S3ImageStorage s3ImageStorage;
  private final KeywordAiClient keywordAiClient;
  private final MarketAnalysisAiClient marketAnalysisAiClient;
  private final NewsRepository newsRepository;
  private final NewsPersistenceService newsPersistenceService;

  public int generateScheduledDartNews() {
    LocalDate today = LocalDate.now();
    log.info("Starting scheduled DART news generation for {}", today);
    int savedCount =
        generateDartNews(today, today, MAJOR_REPORT, KOSPI)
            + generateDartNews(today, today, MAJOR_REPORT, KOSDAQ);

    log.info("Finished scheduled DART news generation: savedCount={}, date={}", savedCount, today);
    return savedCount;
  }

  public int generateDartNews(
      LocalDate beginDate, LocalDate endDate, String disclosureType, String corporationClass) {
    log.info(
        "Starting DART news generation: beginDate={}, endDate={}, disclosureType={}, corporationClass={}",
        beginDate,
        endDate,
        disclosureType,
        corporationClass);

    List<NewsPrompt> prompts =
        dartNewsPromptService.createPrompts(beginDate, endDate, disclosureType, corporationClass);
    log.info(
        "Created {} DART news prompts: disclosureType={}, corporationClass={}",
        prompts.size(),
        disclosureType,
        corporationClass);

    return generateNews(prompts, "DART");
  }

  public int generateKosisNews() {
    log.info("Starting KOSIS news generation");
    List<NewsPrompt> prompts = kosisNewsPromptService.createPrompts();
    log.info("Created {} KOSIS news prompts", prompts.size());

    return generateNews(prompts, "KOSIS");
  }

  public int generateBokNews() {
    log.info("Starting BOK news generation");
    List<NewsPrompt> prompts = bokNewsPromptService.createPrompts();
    log.info("Created {} BOK news prompts", prompts.size());

    return generateNews(prompts, "BOK");
  }

  private int generateNews(List<NewsPrompt> prompts, String sourceName) {
    int savedCount = 0;
    int skippedCount = 0;
    for (NewsPrompt prompt : prompts) {
      if (newsRepository.existsBySourceAndExternalId(prompt.source(), prompt.externalId())) {
        skippedCount++;
        log.info(
            "Skipping existing news: source={}, externalId={}, sourceUrl={}",
            prompt.source(),
            prompt.externalId(),
            prompt.sourceUrl());
        continue;
      }

      log.info(
          "Generating news with AI: source={}, externalId={}, category={}, publishedAt={}",
          prompt.source(),
          prompt.externalId(),
          prompt.category(),
          prompt.publishedAt());
      GeneratedNews generatedNews;
      byte[] image;
      try {
        generatedNews = openAiNewsGenerator.generate(prompt);
        image =
            openAiImageGenerator.generateNewsImage(
                generatedNews.title(), generatedNews.description());
      } catch (BusinessException exception) {
        if (!isAiGenerationFailure(exception)) {
          throw exception;
        }
        skippedCount++;
        log.warn(
            "Skipping news after AI generation failure: source={}, externalId={}, errorCode={}",
            prompt.source(),
            prompt.externalId(),
            exception.errorCode().code());
        continue;
      }
      var keywords = keywordAiClient.extractKeywords(generatedNews.content());
      var marketAnalysis = marketAnalysisAiClient.analyze(generatedNews.content());
      S3ImageStorage.StoredImage uploadedImage = s3ImageStorage.upload(image, "webp", "image/webp");
      String imageUrl = uploadedImage.publicUrl();

      if (!newsPersistenceService.saveIfAbsent(
          prompt, generatedNews, imageUrl, keywords, marketAnalysis)) {
        s3ImageStorage.delete(uploadedImage);
        skippedCount++;
        log.info(
            "Skipping existing news after generation: source={}, externalId={}, sourceUrl={}",
            prompt.source(),
            prompt.externalId(),
            prompt.sourceUrl());
        continue;
      }

      savedCount++;
      log.info(
          "Saved generated news: source={}, externalId={}, title={}",
          prompt.source(),
          prompt.externalId(),
          generatedNews.title());
    }

    log.info(
        "Finished {} news generation: promptCount={}, savedCount={}, skippedCount={}",
        sourceName,
        prompts.size(),
        savedCount,
        skippedCount);
    return savedCount;
  }

  private boolean isAiGenerationFailure(BusinessException exception) {
    return exception.errorCode() == ExternalErrorCode.AI_NEWS_GENERATION_FAILED
        || exception.errorCode() == ExternalErrorCode.AI_IMAGE_GENERATION_FAILED;
  }
}
