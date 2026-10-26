package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.springframework.data.domain.Page;

public record FeaturedPersonPostCandidatePageResponse(
    @Schema(description = "게시물 후보 목록")
        List<FeaturedPersonPostCandidateInboxItemResponse> candidates,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 후보 수", example = "42") long totalElements,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages) {

  public static FeaturedPersonPostCandidatePageResponse from(Page<FeaturedPersonPost> candidates) {
    return new FeaturedPersonPostCandidatePageResponse(
        candidates.stream().map(FeaturedPersonPostCandidateInboxItemResponse::from).toList(),
        candidates.getNumber() + 1,
        candidates.getSize(),
        candidates.getTotalElements(),
        candidates.getTotalPages());
  }
}
