package org.grit.daynomy.news.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.news.dto.NewsPageResponse;
import org.grit.daynomy.news.exception.NewsErrorCode;
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

  private static final int TODAY_NEWS_SIZE = 9;

  private final NewsRepository newsRepository;

  public NewsPageResponse getNewsPage(int page, int size, Category category) {
    Pageable pageable = PageRequest.of(page - 1, size);
    Page<News> newsPage =
        category == null
            ? newsRepository.findAllByOrderByPublishedAtDescIdDesc(pageable)
            : newsRepository.findByCategoryOrderByPublishedAtDescIdDesc(category, pageable);

    return NewsPageResponse.from(newsPage.map(NewsListItemResponse::from));
  }

  public NewsPageResponse getTodayNews() {
    ZoneId zoneId = ZoneId.systemDefault();
    LocalDate today = LocalDate.now(zoneId);
    Instant startInclusive = today.atStartOfDay(zoneId).toInstant();
    Instant endExclusive = today.plusDays(1).atStartOfDay(zoneId).toInstant();

    Page<News> todayNews =
        newsRepository
            .findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
                startInclusive, endExclusive, PageRequest.of(0, TODAY_NEWS_SIZE));

    return NewsPageResponse.from(todayNews.map(NewsListItemResponse::from));
  }

  public NewsDetailResponse getNewsDetail(Long id) {
    return newsRepository
        .findById(id)
        .map(NewsDetailResponse::from)
        .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
  }
}
