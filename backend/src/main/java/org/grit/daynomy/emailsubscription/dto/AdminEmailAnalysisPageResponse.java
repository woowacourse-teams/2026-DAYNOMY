package org.grit.daynomy.emailsubscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminEmailAnalysisPageResponse(
    List<AdminEmailAnalysisItemResponse> items,
    @Schema(description = "0부터 시작하는 페이지 번호") int page,
    int size,
    int totalPages,
    long totalElements,
    boolean hasNext) {
  public static AdminEmailAnalysisPageResponse from(Page<AdminEmailAnalysisItemResponse> page) {
    return new AdminEmailAnalysisPageResponse(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext());
  }
}
