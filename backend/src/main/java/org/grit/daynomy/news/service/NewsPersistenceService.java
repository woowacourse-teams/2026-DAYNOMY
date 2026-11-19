package org.grit.daynomy.news.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.service.AssetContentService;
import org.grit.daynomy.content.service.AssetMentionMatcher;
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

  private static final Set<AssetCategory> LINKABLE_CATEGORIES = Set.of(AssetCategory.STOCK);

  private final NewsRepository newsRepository;
  private final NewsGenerationTopicRepository topicRepository;
  private final AssetRepository assetRepository;
  private final AssetContentService assetContentService;
  private final AssetMentionMatcher assetMentionMatcher;

  @Transactional
  public List<News> saveDrafts(List<GeneratedEconomicNews> generatedNews) {
    List<News> savedDrafts =
        newsRepository.saveAll(generatedNews.stream().map(this::toDraft).toList());
    connectAssets(generatedNews, savedDrafts);
    return savedDrafts;
  }

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
      drafts.add(toDraft(article));
    }
    List<News> savedDrafts = newsRepository.saveAll(drafts);
    connectAssets(generatedNews, savedDrafts);
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

  private News toDraft(GeneratedEconomicNews article) {
    return News.createDraft(
        article.title(), article.content(), null, article.sources(), article.category());
  }

  private void connectAssets(List<GeneratedEconomicNews> generatedNews, List<News> savedDrafts) {
    List<Asset> matchableAssets =
        assetRepository.findAllByCategoryInAndListedTrue(LINKABLE_CATEGORIES);
    for (int index = 0; index < generatedNews.size(); index++) {
      GeneratedEconomicNews article = generatedNews.get(index);
      Set<Long> assetIds = new LinkedHashSet<>();
      assetMentionMatcher.match(article.title(), article.content(), matchableAssets).stream()
          .map(Asset::getId)
          .forEach(assetIds::add);
      Set<String> industryCodes = Set.copyOf(article.industryCodes());
      if (!industryCodes.isEmpty()) {
        assetRepository.findListedStocksByIndustryCodes(industryCodes).stream()
            .map(Asset::getId)
            .forEach(assetIds::add);
      }
      if (!assetIds.isEmpty()) {
        assetContentService.syncNewsContents(savedDrafts.get(index), List.copyOf(assetIds));
      }
    }
  }
}
