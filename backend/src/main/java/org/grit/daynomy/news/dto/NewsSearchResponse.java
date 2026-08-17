package org.grit.daynomy.news.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.springframework.data.domain.Page;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsSearchResponse(
    Category category,
    List<NewsItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static NewsSearchResponse from(Page<News> news, Category category) {
    return new NewsSearchResponse(
        category,
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
