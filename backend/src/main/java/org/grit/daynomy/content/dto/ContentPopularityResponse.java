package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record ContentPopularityResponse(
    @Schema(description = "인기 콘텐츠 목록") List<ContentPopularityItemResponse> items,
    @Schema(description = "집계 기간", example = "7") int periodDays,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages,
    @Schema(description = "전체 콘텐츠 수", example = "42") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {
  public ContentPopularityResponse {
    items = List.copyOf(items);
  }

  public static ContentPopularityResponse from(
      Page<ContentPopularityItemResponse> popularity, int periodDays) {
    return new ContentPopularityResponse(
        popularity.getContent(),
        periodDays,
        popularity.getNumber() + 1,
        popularity.getSize(),
        popularity.getTotalPages(),
        popularity.getTotalElements(),
        popularity.hasNext());
  }
}
