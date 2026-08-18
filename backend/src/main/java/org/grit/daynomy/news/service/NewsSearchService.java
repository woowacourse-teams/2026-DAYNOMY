package org.grit.daynomy.news.service;

import static org.grit.daynomy.common.CommonErrorCode.INVALID_REQUEST;
import static org.grit.daynomy.news.exception.NewsErrorCode.INVALID_SEARCH_KEYWORD;
import static org.grit.daynomy.news.exception.NewsErrorCode.SEARCH_KEYWORD_REQUIRED;

import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsSearchResponse;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsSearchService {

  private static final int MIN_KEYWORD_LENGTH = 1;
  private static final int MAX_KEYWORD_LENGTH = 100;
  private static final int MAX_PAGE_SIZE = 100;

  private final NewsRepository newsRepository;

  public NewsSearchService(NewsRepository newsRepository) {
    this.newsRepository = newsRepository;
  }

  @Transactional(readOnly = true)
  public NewsSearchResponse search(String keyword, Category category, int page, int size) {
    String normalizedKeyword = validateKeyword(keyword);

    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(INVALID_REQUEST);
    }

    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    return NewsSearchResponse.from(newsRepository.search(normalizedKeyword, category, pageable));
  }

  private String validateKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      throw new BusinessException(SEARCH_KEYWORD_REQUIRED);
    }

    String normalizedKeyword = keyword.strip();
    if (normalizedKeyword.length() < MIN_KEYWORD_LENGTH
        || normalizedKeyword.length() > MAX_KEYWORD_LENGTH
        || normalizedKeyword.codePoints().noneMatch(Character::isLetterOrDigit)) {
      throw new BusinessException(INVALID_SEARCH_KEYWORD);
    }
    return normalizedKeyword;
  }
}
