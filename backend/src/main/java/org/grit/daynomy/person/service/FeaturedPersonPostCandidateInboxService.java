package org.grit.daynomy.person.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatePageResponse;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FeaturedPersonPostCandidateInboxService {

  private final FeaturedPersonPostRepository postRepository;

  @Transactional(readOnly = true)
  public FeaturedPersonPostCandidatePageResponse getPendingCandidates(
      int page, int size, Long personId, ContentSourceType sourceType) {
    PageRequest pageable = PageRequest.of(page - 1, size);
    Page<FeaturedPersonPost> candidates;
    if (personId != null && sourceType != null) {
      candidates =
          postRepository.findByPersonIdAndStatusAndSourceTypeOrderByCreatedAtDescIdDesc(
              personId, FeaturedPersonPostStatus.PENDING, sourceType, pageable);
    } else if (personId != null) {
      candidates =
          postRepository.findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
              personId, FeaturedPersonPostStatus.PENDING, pageable);
    } else if (sourceType != null) {
      candidates =
          postRepository.findByStatusAndSourceTypeOrderByCreatedAtDescIdDesc(
              FeaturedPersonPostStatus.PENDING, sourceType, pageable);
    } else {
      candidates =
          postRepository.findByStatusOrderByCreatedAtDescIdDesc(
              FeaturedPersonPostStatus.PENDING, pageable);
    }
    return FeaturedPersonPostCandidatePageResponse.from(candidates);
  }
}
