package org.grit.daynomy.news.service;

import static org.grit.daynomy.common.exception.ErrorCode.BAD_REQUEST;
import static org.grit.daynomy.common.exception.ErrorCode.INVALID_CATEGORY;
import static org.grit.daynomy.common.exception.ErrorCode.INVALID_SEARCH_KEYWORD;
import static org.grit.daynomy.common.exception.ErrorCode.SEARCH_KEYWORD_REQUIRED;

import java.util.Locale;
import org.grit.daynomy.common.exception.ApiException;
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
  public NewsSearchResponse search(String keyword, String categoryValue, int page, int size) {
    String normalizedKeyword = validateKeyword(keyword);
    Category category = parseCategory(categoryValue);

    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ApiException(BAD_REQUEST);
    }

    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    return NewsSearchResponse.from(
        newsRepository.search(normalizedKeyword, category, pageable), category);
  }

  private String validateKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      throw new ApiException(SEARCH_KEYWORD_REQUIRED);
    }

    String normalizedKeyword = keyword.strip();
    if (normalizedKeyword.length() < MIN_KEYWORD_LENGTH
        || normalizedKeyword.length() > MAX_KEYWORD_LENGTH
        || normalizedKeyword.codePoints().noneMatch(Character::isLetterOrDigit)) {
      throw new ApiException(INVALID_SEARCH_KEYWORD);
    }
    return normalizedKeyword;
  }

  private Category parseCategory(String categoryValue) {
    if (categoryValue == null || categoryValue.isBlank()) {
      return null;
    }

    try {
      return Category.valueOf(categoryValue.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ApiException(INVALID_CATEGORY);
    }
  }
}
