package org.grit.daynomy.news.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.repository.NewsGenerationTopicRepository;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NewsPersistenceService {

  private final NewsRepository newsRepository;
  private final NewsGenerationTopicRepository topicRepository;

  @Transactional
  public List<News> saveDrafts(
      List<GeneratedEconomicNews> generatedNews,
      List<Long> reservedTopicIds,
      UUID reservationToken) {
    List<NewsGenerationTopic> topics = topicRepository.findAllByIdForUpdate(reservedTopicIds);
    if (topics.size() != reservedTopicIds.size()) {
      throw new IllegalStateException("Reserved news topics could not be loaded.");
    }
    if (topics.stream().anyMatch(topic -> !topic.isReservedBy(reservationToken))) {
      throw new IllegalStateException("News topic reservation ownership was lost.");
    }
    Map<String, NewsGenerationTopic> topicsByKey = new HashMap<>();
    topics.forEach(topic -> topicsByKey.put(topic.getTopicKey(), topic));

    List<News> drafts = new ArrayList<>();
    for (GeneratedEconomicNews article : generatedNews) {
      if (!topicsByKey.containsKey(article.topicKey())) {
        throw new IllegalStateException("Generated news does not match a reserved topic.");
      }
      drafts.add(
          News.createDraft(
              article.title(), article.content(), null, article.sources(), article.category()));
    }
    List<News> savedDrafts = newsRepository.saveAll(drafts);
    for (int index = 0; index < generatedNews.size(); index++) {
      topicsByKey
          .get(generatedNews.get(index).topicKey())
          .generated(savedDrafts.get(index), reservationToken);
    }
    topics.stream()
        .filter(topic -> topic.isReservedBy(reservationToken))
        .forEach(topic -> topic.release(reservationToken));
    return savedDrafts;
  }
}
