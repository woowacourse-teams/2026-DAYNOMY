package org.grit.daynomy.news.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.repository.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
