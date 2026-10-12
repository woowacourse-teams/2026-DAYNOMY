package org.grit.daynomy.search.service;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.domain.NewsSearchSort;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.grit.daynomy.search.repository.NewsSearchSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsSearchService {

  private final NewsSearchRepository newsSearchRepository;

  public NewsSearchService(NewsSearchRepository newsSearchRepository) {
    this.newsSearchRepository = newsSearchRepository;
  }

  @Transactional(readOnly = true)
  public NewsSearchResponse search(
      String keyword, Category category, int page, int size, NewsSearchSort sort) {
    PageRequest pageable = PageRequest.of(page - 1, size);
    NewsSearchSpecification specification =
        new NewsSearchSpecification(NewsSearchTerms.from(keyword), category, sort);
    return NewsSearchResponse.from(newsSearchRepository.findAll(specification, pageable));
  }
}
