package org.grit.daynomy.keyword.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.keyword.dto.KeywordsResponse;
import org.grit.daynomy.keyword.service.KeywordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Keyword", description = "뉴스 키워드 API")
@RequiredArgsConstructor
@RequestMapping("/api/news/{newsId}/keywords")
@RestController
public class KeywordController {

  private final KeywordService keywordService;

  @Operation(summary = "뉴스 키워드 조회", description = "뉴스 ID로 해당 뉴스의 키워드 목록을 조회합니다.")
  @GetMapping
  public ResponseEntity<KeywordsResponse> getKeywords(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId) {
    return ResponseEntity.ok(keywordService.getKeywords(newsId));
  }
}
