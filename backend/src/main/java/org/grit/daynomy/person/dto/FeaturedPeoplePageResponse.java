package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.springframework.data.domain.Page;

public record FeaturedPeoplePageResponse(
    @Schema(description = "주요 인물 목록과 현재 페이지의 공개 게시물")
        List<FeaturedPersonResponse> people,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "6") int size,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages,
    @Schema(description = "전체 공개 게시물 수", example = "14") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {

  public static FeaturedPeoplePageResponse from(
      List<FeaturedPersonResponse> people, Page<FeaturedPersonPost> posts) {
    return new FeaturedPeoplePageResponse(
        people,
        posts.getNumber() + 1,
        posts.getSize(),
        posts.getTotalPages(),
        posts.getTotalElements(),
        posts.hasNext());
  }
}
