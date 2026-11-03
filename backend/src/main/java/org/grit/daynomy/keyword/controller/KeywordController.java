package org.grit.daynomy.keyword.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
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

  @Operation(
      summary = "뉴스 키워드 조회",
      description =
          "발행된 뉴스의 키워드를 조회합니다. 뉴스가 없거나 미발행 상태이면 404이며, 키워드가 없으면 200과 빈 keywords 배열을 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: newsId가 숫자 형식이 아님",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "NEWS_NOT_FOUND: 발행된 뉴스가 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<KeywordsResponse> getKeywords(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long newsId) {
    return ResponseEntity.ok(keywordService.getKeywords(newsId));
  }
}
