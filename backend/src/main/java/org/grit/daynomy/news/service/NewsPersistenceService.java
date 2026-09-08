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
import org.grit.daynomy.news.domain.NewsSourceInfo;
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
  public void save(
      NewsPrompt prompt,
      GeneratedNews generatedNews,
      String imageUrl,
      List<NewsKeyword> keywords,
      NewsMarketAnalysis marketAnalysis) {
    News news =
        News.createPublished(
            generatedNews.title(),
            generatedNews.content(),
            imageUrl,
            List.of(new NewsSourceInfo(prompt.sourceName(), prompt.sourceUrl())),
            prompt.category(),
            prompt.publishedAt());
    newsRepository.save(news);
    keywordService.saveKeywords(news, keywords);
    marketAnalysisService.saveMarketAnalysis(news, marketAnalysis);
  }
}
