package org.grit.daynomy.keyword.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.keyword.domain.Keyword;

public record KeywordsResponse(@Schema(description = "뉴스 키워드 목록") List<KeywordResponse> keywords) {

  public static KeywordsResponse from(List<Keyword> keywords) {
    return new KeywordsResponse(keywords.stream().map(KeywordResponse::from).toList());
  }
}
