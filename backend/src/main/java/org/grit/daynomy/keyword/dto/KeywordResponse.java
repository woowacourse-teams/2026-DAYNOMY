package org.grit.daynomy.keyword.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;

public record KeywordResponse(
    @Schema(description = KeywordCategory.DEFINITION, example = "POLICY") KeywordCategory category,
    @Schema(description = "키워드", example = "금리 인하") String keyword,
    @Schema(description = "키워드 분석 포인트 3개") List<String> points) {

  public static KeywordResponse from(NewsKeyword keyword) {
    return new KeywordResponse(
        keyword.getCategory(),
        keyword.getKeyword(),
        List.of(keyword.getPoint1(), keyword.getPoint2(), keyword.getPoint3()));
  }
}
