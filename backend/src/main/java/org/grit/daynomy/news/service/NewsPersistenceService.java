package org.grit.daynomy.news.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NewsPersistenceService {

  private final NewsRepository newsRepository;

  @Transactional
  public List<News> saveDrafts(List<GeneratedEconomicNews> generatedNews, List<String> imageUrls) {
    if (generatedNews.size() != imageUrls.size()) {
      throw new IllegalArgumentException(
          "Each generated news article must have an image URL slot.");
    }

    List<News> drafts = new ArrayList<>();
    for (int index = 0; index < generatedNews.size(); index++) {
      GeneratedEconomicNews article = generatedNews.get(index);
      drafts.add(
          News.createDraft(
              article.title(),
              article.content(),
              imageUrls.get(index),
              article.sources(),
              article.category()));
    }
    return newsRepository.saveAll(drafts);
  }
}
