package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grit.daynomy.common.ApiResponse;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsSearchResponse;
import org.grit.daynomy.news.service.NewsSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Tag(name = "뉴스 검색", description = "키워드와 카테고리로 뉴스를 검색합니다.")
@RestController
@RequestMapping("/api/search/news")
public class NewsSearchController {

  private final NewsSearchService newsSearchService;

  public NewsSearchController(NewsSearchService newsSearchService) {
    this.newsSearchService = newsSearchService;
  }

  @Operation(summary = "뉴스 검색", description = "제목·설명·본문에서 키워드를 검색하고 카테고리와 페이지 조건을 적용합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "검색 성공 또는 검색 결과 없음",
        content = @Content(schema = @Schema(implementation = NewsSearchResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "검색어·카테고리·페이지 조건 오류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "서버 오류")
  })
  @GetMapping
  public NewsSearchResponse search(
      @Parameter(description = "검색어(1~100자)", example = "금리", required = true)
          @RequestParam(name = "q", required = false)
          String keyword,
      @Parameter(description = "카테고리. 생략하면 전체 검색", example = "BOND") @RequestParam(required = false)
          Category category,
      @Parameter(description = "페이지 번호(0부터 시작)", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    return newsSearchService.search(keyword, category, page, size);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    ErrorCode errorCode =
        exception.getRequiredType() == Category.class
            ? ErrorCode.INVALID_CATEGORY
            : ErrorCode.INVALID_REQUEST;
    return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode.message()));
  }
}
