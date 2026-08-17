package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record NewsPageResponse(
    @Schema(description = "뉴스 목록") List<NewsListItemResponse> items,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "15") int size,
    @Schema(description = "전체 페이지 수", example = "3") int totalPages,
    @Schema(description = "전체 뉴스 수", example = "42") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {

  public static NewsPageResponse from(Page<NewsListItemResponse> page) {
    return new NewsPageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
