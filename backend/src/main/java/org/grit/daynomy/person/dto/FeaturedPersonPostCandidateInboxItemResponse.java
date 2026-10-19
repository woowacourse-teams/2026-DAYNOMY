package org.grit.daynomy.person.dto;

import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;

public record FeaturedPersonPostCandidateInboxItemResponse(
    Long id,
    Long personId,
    String personName,
    String personProfileImageUrl,
    ContentSourceType sourceType,
    String title,
    String url,
    Instant publishedAt,
    FeaturedPersonPostStatus status,
    Instant createdAt) {

  public static FeaturedPersonPostCandidateInboxItemResponse from(
      FeaturedPersonPost candidate) {
    return new FeaturedPersonPostCandidateInboxItemResponse(
        candidate.getId(),
        candidate.getPerson().getId(),
        candidate.getPerson().getName(),
        candidate.getPerson().getProfileImageUrl(),
        candidate.getSourceType(),
        candidate.getTitle(),
        candidate.getUrl(),
        candidate.getPublishedAt(),
        candidate.getStatus(),
        candidate.getCreatedAt());
  }
}
