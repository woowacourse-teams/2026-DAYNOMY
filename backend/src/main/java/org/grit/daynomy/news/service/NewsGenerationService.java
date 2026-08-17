package org.grit.daynomy.news.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.external.dart.DartNewsPromptService;
import org.grit.daynomy.external.kosis.KosisNewsPromptService;
import org.grit.daynomy.external.openai.OpenAiImageGenerator;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class NewsGenerationService {

  private final DartNewsPromptService dartNewsPromptService;
  private final KosisNewsPromptService kosisNewsPromptService;
  private final OpenAiNewsGenerator openAiNewsGenerator;
  private final OpenAiImageGenerator openAiImageGenerator;
  private final NewsRepository newsRepository;

  @Transactional
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

  @Transactional
  public int generateKosisNews() {
    log.info("Starting KOSIS news generation");
    List<NewsPrompt> prompts = kosisNewsPromptService.createPrompts();
    log.info("Created {} KOSIS news prompts", prompts.size());

    return generateNews(prompts, "KOSIS");
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
      var generatedNews = openAiNewsGenerator.generate(prompt);
      String imageUrl =
          openAiImageGenerator.generateNewsImage(
              generatedNews.title(), generatedNews.description());

      newsRepository.save(
          new News(
              generatedNews.title(),
              generatedNews.content(),
              generatedNews.description(),
              imageUrl,
              prompt.source(),
              prompt.externalId(),
              prompt.sourceUrl(),
              prompt.category(),
              prompt.publishedAt()));
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
}
