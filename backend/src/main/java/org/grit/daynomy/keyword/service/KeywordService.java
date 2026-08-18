package org.grit.daynomy.keyword.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.keyword.domain.Keyword;
import org.grit.daynomy.keyword.dto.KeywordsResponse;
import org.grit.daynomy.keyword.parser.KeywordParser;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class KeywordService {

  private final NewsRepository newsRepository;
  private final NewsKeywordRepository newsKeywordRepository;

  @Transactional
  public void saveKeywords(News news, List<Keyword> keywords) {
    newsKeywordRepository.saveAll(
        keywords.stream().map(keyword -> KeywordParser.toEntity(news, keyword)).toList());
  }

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
