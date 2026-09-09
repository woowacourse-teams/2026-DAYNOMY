package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record AdminNewsCreateRequest(
    @Schema(description = "뉴스 제목", example = "금리 인상 전망에 시장 주목") @NotBlank(message = "제목은 필수입니다.")
        String title,
    @Schema(description = "뉴스 본문", example = "한국은행의 금리 결정에 시장의 관심이 쏠리고 있습니다.")
        @NotBlank(message = "본문은 필수입니다.")
        String content,
    @Schema(description = "뉴스 출처 목록") @NotEmpty(message = "출처는 하나 이상 필요합니다.") @Valid
        List<@NotNull NewsSourceRequest> sources,
    @Schema(description = "뉴스 카테고리", example = "STOCK") @NotNull(message = "카테고리는 필수입니다.")
        Category category) {

  public List<NewsSourceInfo> sourceInfos() {
    return sources.stream()
        .map(request -> new NewsSourceInfo(request.name(), request.url()))
        .toList();
  }
}
