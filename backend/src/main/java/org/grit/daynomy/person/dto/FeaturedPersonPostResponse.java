package org.grit.daynomy.person.dto;

import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;

public record FeaturedPersonPostResponse(
    Long id,
    ContentSourceType sourceType,
    String title,
    String url,
    Instant publishedAt,
    Instant createdAt) {

  public static FeaturedPersonPostResponse from(FeaturedPersonPost post) {
    return new FeaturedPersonPostResponse(
        post.getId(),
        post.getSourceType(),
        post.getTitle(),
        post.getUrl(),
        post.getPublishedAt(),
        post.getCreatedAt());
  }
}
