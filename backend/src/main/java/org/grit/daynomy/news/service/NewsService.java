package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.news.dto.NewsPageResponse;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class NewsService {

  private final NewsRepository newsRepository;

  public NewsPageResponse getNewsPage(int page, int size, Category category) {
    if (page < 1 || size < 1) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Pageable pageable = PageRequest.of(page - 1, size);
    Page<News> newsPage =
        category == null
            ? newsRepository.findAllByOrderByPublishedAtDescIdDesc(pageable)
            : newsRepository.findByCategoryOrderByPublishedAtDescIdDesc(category, pageable);

    return NewsPageResponse.from(newsPage.map(NewsListItemResponse::from));
  }

  public NewsDetailResponse getNewsDetail(Long id) {
    return newsRepository
        .findById(id)
        .map(NewsDetailResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_NOT_FOUND));
  }
}
