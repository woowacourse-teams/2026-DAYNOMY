package org.grit.daynomy.keyword.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.keyword.domain.Keyword;

public record KeywordResponse(
    @Schema(description = "키워드", example = "금리 인하") String keyword,
    @Schema(description = "키워드 설명", example = "대출 수요 회복과 연결되는 주요 정책 변수") String description) {

  public static KeywordResponse from(Keyword keyword) {
    return new KeywordResponse(keyword.getKeyword(), keyword.getDescription());
  }
}
