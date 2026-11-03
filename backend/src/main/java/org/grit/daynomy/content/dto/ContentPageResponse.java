package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record ContentPageResponse(
    @Schema(description = "콘텐츠 목록") List<AssetContentResponse> items,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "12") int size,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages,
    @Schema(description = "전체 콘텐츠 수", example = "30") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {
  public static ContentPageResponse from(Page<AssetContentResponse> page) {
    return new ContentPageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
