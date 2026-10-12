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
import org.grit.daynomy.search.domain.NewsSearchSort;
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

  @Operation(
      summary = "뉴스 검색",
      description =
          "공백으로 구분한 모든 단어가 제목 또는 본문에 포함된 발행 뉴스를 검색합니다. "
              + "반복 공백·중복 단어·영문 대소문자를 정규화하며 %, _, !는 일반 문자로 처리합니다. "
              + "기본 최신순 또는 제목에 일치하는 단어 수를 우선하는 관련도순으로 정렬합니다. "
              + "동점은 발행일·ID 내림차순으로 정렬합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공 또는 검색 결과 없음",
        content = @Content(schema = @Schema(implementation = NewsSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "검색어·카테고리·페이지·정렬 조건 오류",
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
      @Parameter(description = "카테고리. 생략하면 전체 검색", example = "ETF") @RequestParam(required = false)
          Category category,
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size,
      @Parameter(description = "정렬 방식. LATEST(최신순), RELEVANCE(제목 일치 우선)", example = "LATEST")
          @RequestParam(defaultValue = "LATEST")
          NewsSearchSort sort) {
    return newsSearchService.search(keyword, category, page, size, sort);
  }
}
