package org.grit.daynomy.keyword.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.keyword.dto.KeywordsResponse;
import org.grit.daynomy.keyword.parser.KeywordParser;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class KeywordService {

  private final NewsRepository newsRepository;
  private final NewsKeywordRepository newsKeywordRepository;

  public KeywordsResponse getKeywords(Long newsId) {
    if (!newsRepository.existsById(newsId)) {
      throw new BusinessException(ErrorCode.NEWS_NOT_FOUND);
    }

    return KeywordsResponse.from(
        newsKeywordRepository.findByNewsIdOrderByIdAsc(newsId).stream()
            .map(KeywordParser::toDomain)
            .toList());
  }
}
