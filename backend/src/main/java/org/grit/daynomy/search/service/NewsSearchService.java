package org.grit.daynomy.search.service;

import static org.grit.daynomy.search.exception.SearchErrorCode.SEARCH_INVALID_KEYWORD;
import static org.grit.daynomy.search.exception.SearchErrorCode.SEARCH_INVALID_PAGE_CONDITION;
import static org.grit.daynomy.search.exception.SearchErrorCode.SEARCH_KEYWORD_REQUIRED;

import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsSearchService {

  private static final int MIN_KEYWORD_LENGTH = 1;
  private static final int MAX_KEYWORD_LENGTH = 100;
  private static final int MAX_PAGE_SIZE = 100;

  private final NewsSearchRepository newsSearchRepository;

  public NewsSearchService(NewsSearchRepository newsSearchRepository) {
    this.newsSearchRepository = newsSearchRepository;
  }

  @Transactional(readOnly = true)
  public NewsSearchResponse search(String keyword, Category category, int page, int size) {
    String normalizedKeyword = validateKeyword(keyword);

    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(SEARCH_INVALID_PAGE_CONDITION);
    }

    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    return NewsSearchResponse.from(
        newsSearchRepository.search(normalizedKeyword, category, pageable));
  }

  private String validateKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      throw new BusinessException(SEARCH_KEYWORD_REQUIRED);
    }

    String normalizedKeyword = keyword.strip();
    if (normalizedKeyword.length() < MIN_KEYWORD_LENGTH
        || normalizedKeyword.length() > MAX_KEYWORD_LENGTH
        || normalizedKeyword.codePoints().noneMatch(Character::isLetterOrDigit)) {
      throw new BusinessException(SEARCH_INVALID_KEYWORD);
    }
    return normalizedKeyword;
  }
}
