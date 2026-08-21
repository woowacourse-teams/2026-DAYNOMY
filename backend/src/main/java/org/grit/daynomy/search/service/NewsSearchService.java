package org.grit.daynomy.search.service;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsSearchService {

  private final NewsSearchRepository newsSearchRepository;

  public NewsSearchService(NewsSearchRepository newsSearchRepository) {
    this.newsSearchRepository = newsSearchRepository;
  }

  @Transactional(readOnly = true)
  public NewsSearchResponse search(String keyword, Category category, int page, int size) {
    String escapedKeyword = escapeLikeKeyword(keyword.strip());

    PageRequest pageable =
        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    return NewsSearchResponse.from(newsSearchRepository.search(escapedKeyword, category, pageable));
  }

  private String escapeLikeKeyword(String keyword) {
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
