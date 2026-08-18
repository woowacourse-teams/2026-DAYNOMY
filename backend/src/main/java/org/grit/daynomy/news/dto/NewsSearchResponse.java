package org.grit.daynomy.news.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.springframework.data.domain.Page;

public record NewsSearchResponse(
    List<NewsItem> content, int page, int size, long totalElements, int totalPages) {

  public static NewsSearchResponse from(Page<News> news) {
    return new NewsSearchResponse(
        news.getContent().stream().map(NewsItem::from).toList(),
        news.getNumber(),
        news.getSize(),
        news.getTotalElements(),
        news.getTotalPages());
  }

  public record NewsItem(Long id, String title, Category category, LocalDateTime publishedAt) {

    private static NewsItem from(News news) {
      return new NewsItem(news.getId(), news.getTitle(), news.getCategory(), news.getPublishedAt());
    }
  }
}
