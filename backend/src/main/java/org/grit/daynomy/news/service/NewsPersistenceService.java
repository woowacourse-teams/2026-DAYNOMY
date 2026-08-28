package org.grit.daynomy.news.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.service.KeywordService;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.service.MarketAnalysisService;
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
  private final KeywordService keywordService;
  private final MarketAnalysisService marketAnalysisService;

  @Transactional
  public boolean saveIfAbsent(
      NewsPrompt prompt,
      GeneratedNews generatedNews,
      String imageUrl,
      List<NewsKeyword> keywords,
      NewsMarketAnalysis marketAnalysis) {
    if (newsRepository.existsBySourceAndExternalId(prompt.source(), prompt.externalId())) {
      return false;
    }

    News news =
        new News(
            generatedNews.title(),
            generatedNews.content(),
            generatedNews.description(),
            imageUrl,
            prompt.source(),
            prompt.externalId(),
            prompt.sourceUrl(),
            prompt.category(),
            prompt.publishedAt());
    newsRepository.save(news);
    keywordService.saveKeywords(news, keywords);
    marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);
    return true;
  }
}
