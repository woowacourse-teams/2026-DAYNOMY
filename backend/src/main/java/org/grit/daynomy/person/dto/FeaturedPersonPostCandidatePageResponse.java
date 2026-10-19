package org.grit.daynomy.person.dto;

import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.springframework.data.domain.Page;

public record FeaturedPersonPostCandidatePageResponse(
    List<FeaturedPersonPostCandidateInboxItemResponse> candidates,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static FeaturedPersonPostCandidatePageResponse from(
      Page<FeaturedPersonPost> candidates) {
    return new FeaturedPersonPostCandidatePageResponse(
        candidates.stream().map(FeaturedPersonPostCandidateInboxItemResponse::from).toList(),
        candidates.getNumber() + 1,
        candidates.getSize(),
        candidates.getTotalElements(),
        candidates.getTotalPages());
  }
}
