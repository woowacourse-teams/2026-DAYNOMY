package org.grit.daynomy.news.dto;

import java.time.LocalDateTime;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;

public record NewsDetailResponse(
    Long id,
    String title,
    String content,
    String description,
    String imageUrl,
    Category category,
    LocalDateTime publishedAt) {

  public static NewsDetailResponse from(News news) {
    return new NewsDetailResponse(
        news.getId(),
        news.getTitle(),
        news.getContent(),
        news.getDescription(),
        news.getImageUrl(),
        news.getCategory(),
        news.getPublishedAt());
  }
}
