package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminNewsKeywordGenerationRequest(
    @Schema(description = "생성할 이슈의 검색 키워드", example = "미국 중간선거")
        @NotBlank(message = "키워드는 필수입니다.")
        @Size(min = 2, max = 100, message = "키워드는 2자 이상 100자 이하여야 합니다.")
        String keyword) {

  public AdminNewsKeywordGenerationRequest {
    keyword = keyword == null ? null : keyword.strip();
  }
}
