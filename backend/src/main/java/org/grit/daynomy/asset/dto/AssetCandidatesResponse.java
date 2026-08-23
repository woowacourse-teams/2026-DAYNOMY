package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record AssetCandidatesResponse(
    @Schema(description = "데이터 기준일자", example = "20260821") String baseDate,
    @Schema(description = "코스닥 대표 종목 순위 목록") List<AssetCandidateResponse> rankings,
    @Schema(description = "현재 페이지 번호", example = "1") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 페이지 수", example = "8") int totalPages,
    @Schema(description = "전체 종목 수", example = "150") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {

  public static AssetCandidatesResponse from(String baseDate, Page<AssetCandidateResponse> page) {
    return new AssetCandidatesResponse(
        baseDate,
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }

  public static AssetCandidatesResponse empty(int page, int size) {
    return new AssetCandidatesResponse(null, List.of(), page, size, 0, 0, false);
  }
}
