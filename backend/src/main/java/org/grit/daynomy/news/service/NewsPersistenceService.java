package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NewsPersistenceService {

  private final NewsRepository newsRepository;

  @Transactional
  public boolean saveIfAbsent(NewsPrompt prompt, GeneratedNews generatedNews, String imageUrl) {
    if (newsRepository.existsBySourceAndExternalId(prompt.source(), prompt.externalId())) {
      return false;
    }

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
    return true;
  }
}
