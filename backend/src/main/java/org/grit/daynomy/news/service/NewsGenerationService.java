package org.grit.daynomy.news.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.News;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class NewsGenerationService {

  private final OpenAiNewsGenerator openAiNewsGenerator;
  private final NewsPersistenceService newsPersistenceService;

  public List<News> generateKoreanEconomicNewsDrafts() {
    return saveDrafts("korea", openAiNewsGenerator.generateEconomicNews());
  }

  public List<News> generateUsEconomicNewsDrafts() {
    return saveDrafts("us", openAiNewsGenerator.generateUsEconomicNews());
  }

  private List<News> saveDrafts(String generationType, List<GeneratedEconomicNews> generatedNews) {
    long startedAt = System.nanoTime();
    List<String> imageUrls = Collections.nCopies(generatedNews.size(), null);
    List<News> drafts = newsPersistenceService.saveDrafts(generatedNews, imageUrls);
    log.atInfo()
        .addKeyValue("event", LogEvent.NEWS_GENERATION_COMPLETED.code())
        .addKeyValue("generationType", generationType)
        .addKeyValue("generatedCount", generatedNews.size())
        .addKeyValue("savedCount", drafts.size())
        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
        .log(LogEvent.NEWS_GENERATION_COMPLETED.message());
    return drafts;
  }
}
