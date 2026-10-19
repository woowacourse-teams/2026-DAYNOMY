package org.grit.daynomy.person.dto;

import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;

public record FeaturedPersonPostCandidateResponse(
    Long id,
    ContentSourceType sourceType,
    String title,
    String url,
    Instant publishedAt,
    FeaturedPersonPostStatus status,
    Instant createdAt) {

  public static FeaturedPersonPostCandidateResponse from(FeaturedPersonPost candidate) {
    return new FeaturedPersonPostCandidateResponse(
        candidate.getId(),
        candidate.getSourceType(),
        candidate.getTitle(),
        candidate.getUrl(),
        candidate.getPublishedAt(),
        candidate.getStatus(),
        candidate.getCreatedAt());
  }
}
