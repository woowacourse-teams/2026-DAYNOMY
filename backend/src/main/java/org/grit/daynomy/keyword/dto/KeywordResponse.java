package org.grit.daynomy.keyword.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;

public record KeywordResponse(
    @Schema(description = KeywordCategory.DEFINITION, example = "POLICY") KeywordCategory category,
    @Schema(description = "본문에 포함된 2~5글자의 핵심 키워드", example = "공모가") String keyword,
    @Schema(description = "키워드의 쉬운 뜻", example = "기업이 상장할 때 처음 주식을 판매하는 가격입니다.") String definition,
    @Schema(description = "키워드가 기사에서 갖는 의미") String context,
    @Schema(description = "키워드를 이해할 때 추가로 확인할 사항") String watchPoint) {

  public static KeywordResponse from(NewsKeyword keyword) {
    return new KeywordResponse(
        keyword.getCategory(),
        keyword.getKeyword(),
        keyword.getDefinition(),
        keyword.getContext(),
        keyword.getWatchPoint());
  }
}
