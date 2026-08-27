package org.grit.daynomy.keyword.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.dto.KeywordsResponse;
import org.grit.daynomy.keyword.exception.KeywordErrorCode;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.exception.NewsErrorCode;
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
  public void saveKeywords(News news, List<NewsKeyword> keywords) {
    newsKeywordRepository.saveAll(
        keywords.stream()
            .map(
                keyword ->
                    new NewsKeyword(
                        news,
                        keyword.getCategory(),
                        keyword.getKeyword(),
                        keyword.getDescription()))
            .toList());
  }

  public KeywordsResponse getKeywords(Long newsId) {
    if (!newsRepository.existsById(newsId)) {
      throw new BusinessException(NewsErrorCode.NEWS_NOT_FOUND);
    }

    List<NewsKeyword> keywords = newsKeywordRepository.findByNewsIdOrderByIdAsc(newsId);
    if (keywords.isEmpty()) {
      throw new BusinessException(KeywordErrorCode.KEYWORD_NOT_FOUND);
    }

    return KeywordsResponse.from(keywords);
  }
}
