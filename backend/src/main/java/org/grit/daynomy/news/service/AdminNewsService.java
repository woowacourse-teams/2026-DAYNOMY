package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsListItemResponse;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AdminNewsService {

  private final NewsRepository newsRepository;

  @Transactional
  public News createDraft(AdminNewsCreateRequest request) {
    News news =
        News.createAdminDraft(
            request.title(),
            request.content(),
            request.description(),
            request.imageUrl(),
            request.sourceUrl(),
            request.category());

    return newsRepository.save(news);
  }

  public AdminNewsPageResponse getNewsPage(
      int page, int size, NewsStatus status, Category category) {
    Page<News> newsPage =
        newsRepository.findAdminNews(status, category, PageRequest.of(page - 1, size));

    return AdminNewsPageResponse.from(newsPage.map(AdminNewsListItemResponse::from));
  }

  public News getNewsDetail(Long id) {
    return newsRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
  }

  @Transactional
  public News update(Long id, AdminNewsUpdateRequest request) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    news.update(
        request.title(),
        request.content(),
        request.description(),
        request.imageUrl(),
        request.sourceUrl(),
        request.category());
    return news;
  }

  @Transactional
  public void delete(Long id) {
    News news =
        newsRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    news.delete();
  }
}
