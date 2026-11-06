package org.grit.daynomy.news.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.external.openai.OpenAiNewsTopicDeduplicator;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class NewsGenerationService {

  private static final int MIN_TOPIC_COUNT = 4;
  private static final int MAX_TOPIC_COUNT = 5;
  private static final int RECENT_TOPIC_DAYS = 7;
  private static final int MAX_COMPARISON_COUNT = 50;

  private final OpenAiNewsGenerator openAiNewsGenerator;
  private final OpenAiNewsTopicDeduplicator topicDeduplicator;
  private final NewsGenerationTopicService topicService;
  private final NewsGenerationDiscoveryLock discoveryLock;
  private final NewsPersistenceService newsPersistenceService;

  public List<News> generateKoreanEconomicNewsDrafts() {
    return generateDrafts(NewsGenerationType.KOREA);
  }

  public List<News> generateUsEconomicNewsDrafts() {
    return generateDrafts(NewsGenerationType.US);
  }

  private List<News> generateDrafts(NewsGenerationType generationType) {
    long startedAt = System.nanoTime();
    List<NewsGenerationTopic> reserved =
        new ArrayList<>(topicService.reserveReady(generationType, MAX_TOPIC_COUNT));
    try {
      if (reserved.size() < MIN_TOPIC_COUNT) {
        int reservedCount = reserved.size();
        Optional<List<NewsGenerationTopic>> discovered =
            discoveryLock.withLock(
                generationType, () -> discoverAndReserve(generationType, reservedCount));
        if (discovered.isEmpty()) {
          throw new BusinessException(NewsErrorCode.NEWS_GENERATION_IN_PROGRESS);
        }
        reserved.addAll(discovered.get());
      }

      if (reserved.isEmpty()) {
        logCompletion(generationType, 0, 0, startedAt);
        return List.of();
      }

      List<GeneratedEconomicNews> generatedNews =
          openAiNewsGenerator.write(
              reserved.stream().map(NewsGenerationTopic::toResearch).toList());
      List<News> drafts =
          newsPersistenceService.saveDrafts(
              generatedNews, reserved.stream().map(NewsGenerationTopic::getId).toList());
      logCompletion(generationType, generatedNews.size(), drafts.size(), startedAt);
      return drafts;
    } catch (RuntimeException exception) {
      topicService.release(reserved.stream().map(NewsGenerationTopic::getId).toList());
      throw exception;
    }
  }

  private List<NewsGenerationTopic> discoverAndReserve(
      NewsGenerationType generationType, int alreadyReserved) {
    int remaining = MAX_TOPIC_COUNT - alreadyReserved;
    List<NewsGenerationTopic> additional =
        new ArrayList<>(topicService.reserveReady(generationType, remaining));
    try {
      if (alreadyReserved + additional.size() >= MIN_TOPIC_COUNT) {
        return additional;
      }

      LocalDate firstRecentDate =
          LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(RECENT_TOPIC_DAYS - 1L);
      List<NewsGenerationTopic> recentTopics =
          topicService.findRecent(generationType, firstRecentDate, MAX_COMPARISON_COUNT);
      var researched =
          generationType == NewsGenerationType.KOREA
              ? openAiNewsGenerator.researchEconomicNews()
              : openAiNewsGenerator.researchUsEconomicNews();
      var uniqueResearch = topicDeduplicator.removeDuplicates(researched, recentTopics);
      topicService.register(generationType, uniqueResearch);
      additional.addAll(
          topicService.reserveReady(
              generationType, MAX_TOPIC_COUNT - alreadyReserved - additional.size()));
      return additional;
    } catch (RuntimeException exception) {
      topicService.release(additional.stream().map(NewsGenerationTopic::getId).toList());
      throw exception;
    }
  }

  private void logCompletion(
      NewsGenerationType generationType, int generatedCount, int savedCount, long startedAt) {
    log.atInfo()
        .addKeyValue("event", LogEvent.NEWS_GENERATION_COMPLETED.code())
        .addKeyValue("generationType", generationType.name().toLowerCase())
        .addKeyValue("generatedCount", generatedCount)
        .addKeyValue("savedCount", savedCount)
        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
        .log(LogEvent.NEWS_GENERATION_COMPLETED.message());
  }
}
