package org.grit.daynomy.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.grit.daynomy.search.service.NewsSearchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "뉴스 검색", description = "키워드와 카테고리로 뉴스를 검색합니다.")
@Validated
@RestController
@RequestMapping("/api/search/news")
public class NewsSearchController {

  private final NewsSearchService newsSearchService;

  public NewsSearchController(NewsSearchService newsSearchService) {
    this.newsSearchService = newsSearchService;
  }

  @Operation(summary = "뉴스 검색", description = "제목·설명·본문에서 키워드를 검색하고 카테고리와 페이지 조건을 적용합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공 또는 검색 결과 없음",
        content = @Content(schema = @Schema(implementation = NewsSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "검색어·카테고리·페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public NewsSearchResponse search(
      @Parameter(description = "검색어(1~100자)", example = "금리", required = true)
          @RequestParam(name = "q", required = false)
          @NotBlank(message = "검색어를 입력해주세요.")
          @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
          @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "올바른 검색어를 입력해주세요.")
          String keyword,
      @Parameter(description = "카테고리. 생략하면 전체 검색", example = "BOND") @RequestParam(required = false)
          Category category,
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size) {
    return newsSearchService.search(keyword, category, page, size);
  }
}
